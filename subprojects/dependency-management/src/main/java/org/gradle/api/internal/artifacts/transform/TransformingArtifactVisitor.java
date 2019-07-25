/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.PreResolvedResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.file.FileCollectionLeafVisitor;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.local.model.ComponentFileArtifactIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.File;
import java.util.Map;

class TransformingArtifactVisitor implements ArtifactVisitor {
    private final ArtifactVisitor visitor;
    private final AttributeContainerInternal target;
    private final Map<ComponentArtifactIdentifier, TransformationResult> artifactResults;

    TransformingArtifactVisitor(ArtifactVisitor visitor, AttributeContainerInternal target, Map<ComponentArtifactIdentifier, TransformationResult> artifactResults) {
        this.visitor = visitor;
        this.target = target;
        this.artifactResults = artifactResults;
    }

    @Override
    public void visitArtifact(DisplayName variantName, AttributeContainer variantAttributes, ResolvableArtifact artifact) {
        TransformationResult result = artifactResults.get(artifact.getId());
        result.getTransformedSubject().ifSuccessfulOrElse(
            transformedSubject -> {
                ResolvedArtifact sourceArtifact = artifact.toPublicView();
                for (File output : transformedSubject.getFiles()) {
                    IvyArtifactName artifactName = DefaultIvyArtifactName.forFile(output, sourceArtifact.getClassifier());
                    ComponentArtifactIdentifier newId = new ComponentFileArtifactIdentifier(sourceArtifact.getId().getComponentIdentifier(), artifactName);
                    ResolvableArtifact resolvedArtifact = new PreResolvedResolvableArtifact(sourceArtifact.getModuleVersion().getId(), artifactName, newId, output, artifact);
                    visitor.visitArtifact(variantName, target, resolvedArtifact);
                }
            },
            failure -> visitor.visitFailure(
                new TransformException(String.format("Failed to transform %s to match attributes %s.", artifact.getId(), target), failure))
        );
    }

    @Override
    public void visitFailure(Throwable failure) {
        visitor.visitFailure(failure);
    }

    @Override
    public boolean startVisit(FileCollectionLeafVisitor.CollectionType collectionType) {
        return visitor.startVisit(collectionType);
    }

    @Override
    public boolean includeFiles() {
        return visitor.includeFiles();
    }

    @Override
    public boolean requireArtifactFiles() {
        return visitor.requireArtifactFiles();
    }
}

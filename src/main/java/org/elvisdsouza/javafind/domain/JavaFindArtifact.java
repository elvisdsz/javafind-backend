package org.elvisdsouza.javafind.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.artifact.Gav;
import org.apache.maven.index.artifact.GavCalculator;
import org.apache.maven.index.artifact.M2GavCalculator;
import org.apache.maven.index.context.IndexingContext;

@Data
@AllArgsConstructor
public class JavaFindArtifact {
    private String name;
    private String description;
    private String groupId;
    private String artifactId;
    private String version;
    private String classifier;
    private String fileExtension;
    private String relFilepath;

    public JavaFindArtifact() {
    }

    public JavaFindArtifact(ArtifactInfo ai) {
        // ai.calculateGav();
        this.name = ai.getName();
        this.description = ai.getDescription();
        this.groupId = ai.getGroupId();
        this.artifactId = ai.getArtifactId();
        this.version = ai.getVersion();
        this.classifier = ai.getClassifier();
        this.fileExtension = ai.getFileExtension();
        this.relFilepath = toRelUrlPath();
    }

    public Gav toGav() {
        return new Gav(this.groupId, this.artifactId, this.version, this.classifier, this.fileExtension,
                null, null, null, false, null, false, null );
    }

    public String toUrlPath(IndexingContext indexingContext) {
        return indexingContext.getRepositoryUrl() + indexingContext.getGavCalculator().gavToPath(this.toGav());
    }

    public String toRelUrlPath() {
        GavCalculator gavCalculator = new M2GavCalculator();
        return gavCalculator.gavToPath(this.toGav());
    }
}

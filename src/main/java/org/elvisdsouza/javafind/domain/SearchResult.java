package org.elvisdsouza.javafind.domain;

import lombok.Data;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.artifact.Gav;
import org.apache.maven.index.context.IndexingContext;

@Data
public class SearchResult {
    private String groupId;
    private String artifactId;
    private String version;
    private String classifier;
    private String fileExtension;

    public SearchResult(ArtifactInfo ai) {
        this.groupId = ai.getGroupId();
        this.artifactId = ai.getArtifactId();
        this.version = ai.getVersion();
        this.classifier = ai.getClassifier();
        this.fileExtension = ai.getFileExtension();
    }

    public Gav toGav() {
        return new Gav(this.groupId, this.artifactId, this.version, this.classifier, this.fileExtension,
                null, null, null, false, null, false, null );
    }

    public String toUrlPath(IndexingContext indexingContext) {
        return indexingContext.getRepositoryUrl() + indexingContext.getGavCalculator().gavToPath(this.toGav());
    }
}

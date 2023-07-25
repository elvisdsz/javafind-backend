package org.elvisdsouza.javafind.domain;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class SearchResult {
    private List<JavaFindArtifact> artifacts;
    private int totalResultCount;
}

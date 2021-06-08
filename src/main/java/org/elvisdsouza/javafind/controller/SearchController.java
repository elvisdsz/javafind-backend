package org.elvisdsouza.javafind.controller;

import org.elvisdsouza.javafind.domain.SearchResult;
import org.elvisdsouza.javafind.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@CrossOrigin // Todo: Remove. Test Only
@RestController
public class SearchController {

    @Autowired
    private SearchService searchService;

    @GetMapping("/searcha")
    public List<SearchResult> searchForArtifacts(@RequestParam(name = "q") String queryString) throws IOException {
        return searchService.searchUserInput(queryString);
    }
}

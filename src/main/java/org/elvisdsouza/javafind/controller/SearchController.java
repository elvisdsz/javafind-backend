package org.elvisdsouza.javafind.controller;

import org.elvisdsouza.javafind.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class SearchController {

    @Autowired
    private SearchService searchService;

    @GetMapping("/searcha")
    public String searchForArtifacts(@RequestParam String queryString) throws IOException {
        return searchService.searchUserInput(queryString);
    }
}

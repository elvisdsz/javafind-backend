package org.elvisdsouza.javafind.controller;

import org.elvisdsouza.javafind.domain.JavaFindArtifact;
import org.elvisdsouza.javafind.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@CrossOrigin // Todo: Remove. Test Only
@RestController
public class SearchController {

    @Autowired
    private SearchService searchService;

    @GetMapping("/searcha")
    public List<JavaFindArtifact> searchForArtifacts(@RequestParam(name = "q") String queryString) throws IOException {
        return searchService.searchUserInput(queryString);
    }

    //@PostMapping("/getFile")
    @GetMapping("/getFile")
    public ResponseEntity<byte[]> getFile(@RequestParam("fp") String relFilepath){
        byte[] contents = searchService.getFileBytes(relFilepath);
        HttpHeaders headers = new HttpHeaders();
        //headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        String filename = "required.jar";
        headers.setContentDispositionFormData(filename, filename);
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
        ResponseEntity<byte[]> response = new ResponseEntity<byte[]>(contents, headers, HttpStatus.OK);
        return response;
    }
}

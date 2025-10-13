package org.com.code.im.controller;

import org.com.code.im.pojo.SensitiveDictObj;
import org.com.code.im.responseHandler.ResponseHandler;
import org.com.code.im.utils.DFAFilter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SensitiveDictController {

    @PostMapping("/api/sensitiveDict/addWord")
    public ResponseHandler addWord(@RequestBody SensitiveDictObj word) {

        if(word == null)
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "参数为空");
        String[] words = word.getWord().split(",");
        for (String w : words) {
            DFAFilter.addWord(w);
        }
        DFAFilter.saveSensitiveDict();
        return new ResponseHandler(ResponseHandler.SUCCESS, "添加成功");
    }
}

package org.com.code.im.utils;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.com.code.im.exception.VideoParseException;

import java.io.IOException;
import java.io.InputStream;

public class VideoMetadataExtractor {

    public static double getDuration(TikaInputStream durationStream) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        Metadata metadata = new Metadata();
        parser.parse(durationStream, new BodyContentHandler(), metadata, new ParseContext());

        // 获取视频片段的时长
        String duration = metadata.get("xmpDM:duration");
        return Double.parseDouble(duration)/ 60; // 转换为分钟
    }
}

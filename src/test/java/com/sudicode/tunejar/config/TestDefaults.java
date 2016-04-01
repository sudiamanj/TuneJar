package com.sudicode.tunejar.config;

import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class TestDefaults {

    public static final Path RESOURCES;

    /**
     * Maps sample music files to the URLs where they can be downloaded if
     * missing.
     */
    public static final Map<File, URL> SAMPLE_MUSIC_MAP;

    static {
        RESOURCES = Paths.get("src", "test", "resources");
        SAMPLE_MUSIC_MAP = getSampleMusicMap();
    }

    private TestDefaults() {}

    private static Map<File, URL> getSampleMusicMap() {
        Map<File, URL> sampleMusicMap = new HashMap<>();
        String[] sampleMusicFiles = new String[] {"AfterDark.mp3", "CrunkKnight.m4a", "Cute.wav"};
        for (String file : sampleMusicFiles) {
            try {
                String fileType = FilenameUtils.getExtension(file);
                if (fileType.equals("m4a")) {
                    fileType = "mp4";
                }
                URL url = new URL("http://sudicode.com/tunejar/Sample-Music/" + fileType + "/" + file);
                sampleMusicMap.put(RESOURCES.resolve(fileType).resolve(file).toFile(), url);
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
        return Collections.unmodifiableMap(sampleMusicMap);
    }

}

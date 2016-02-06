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
import java.util.function.Consumer;

public class TestDefaults {

	public static final Path RESOURCES;

	/**
	 * Maps sample music files to the URLs where they can be downloaded if
	 * missing.
	 */
	public static final Map<File, URL> SAMPLE_MUSIC_MAP;

	static {
		// Directories
		RESOURCES = Paths.get("src", "test", "resources");

		// Sample Music
		Map<File, URL> sampleMusicMap = new HashMap<>();
		Consumer<String> sampleMusicConsumer = (str) -> {
			try {
				String fileType = FilenameUtils.getExtension(str);
				if (fileType.equals("m4a")) {
					fileType = "mp4";
				}
				URL url = new URL("http://sudicode.com/tunejar/Sample-Music/" + fileType + "/" + str);
				sampleMusicMap.put(RESOURCES.resolve(fileType).resolve(str).toFile(), url);
			} catch (MalformedURLException e) {
				throw new RuntimeException(e);
			}
		};
		String[] sampleMusicArr = new String[] { "AfterDark.mp3", "Dubstep.mp3", "Highrider.mp3", "MorningCruise.mp3",
				"QueenOfTheNight.mp3", "CrunkKnight.m4a", "MeatballParade.m4a", "Cute.wav", "FunnySong.wav",
				"LittleIdea.wav" };
		for (String s : sampleMusicArr) {
			sampleMusicConsumer.accept(s);
		}
		SAMPLE_MUSIC_MAP = Collections.unmodifiableMap(sampleMusicMap);
	}

	private TestDefaults() {}

}
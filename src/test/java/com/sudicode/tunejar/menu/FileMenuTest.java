package com.sudicode.tunejar.menu;

import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

import org.junit.Test;
import org.loadui.testfx.GuiTest;

import com.sudicode.tunejar.config.Defaults;
import com.sudicode.tunejar.player.PlayerTest;
import com.sudicode.tunejar.song.Playlist;

import javafx.collections.ObservableList;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;

public class FileMenuTest extends PlayerTest {

	@Test
	public void testNewPlaylist() throws Exception {
		Callable<ObservableList<Playlist>> items = () -> getController().getPlaylistTable().getItems();
		int index = items.call().size();

		getDriver().clickOn("File").clickOn("New...").clickOn("Playlist");
		TextField name = GuiTest.find(Defaults.PLAYLIST_NAME);
		getDriver().type(KeyCode.T, KeyCode.E, KeyCode.S, KeyCode.T, KeyCode.DIGIT0);
		assertTrue(name.getText().equals("test0"));
		getDriver().clickOn("OK");
		assertTrue(items.call().get(index).getName().equals("test0"));
		assertTrue(Files.exists(Paths.get(Defaults.PLAYLISTS_FOLDER, "test0.m3u")));
	}

}
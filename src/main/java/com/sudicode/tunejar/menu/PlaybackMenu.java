package com.sudicode.tunejar.menu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sudicode.tunejar.player.PlayerController;

/**
 * Helper class for handling the Playback menu.
 */
public class PlaybackMenu extends PlayerMenu {

	private static final Logger LOGGER = LoggerFactory.getLogger(PlaybackMenu.class);

	public PlaybackMenu(PlayerController controller) {
		super(controller);
	}

	/**
	 * Plays or resumes the selected song.
	 */
	public void play() {
		int index = controller.getSongTable().getFocusModel().getFocusedIndex();
		if (controller.getSongList().isEmpty() || index < 0 || index >= controller.getSongList().size()) {
			controller.getStatus().setText("No song selected.");
			return;
		}
		controller.getShortcutPause().setText("Pause");
		controller.getMenuPause().setText("Pause");
		play(index);
	}

	/**
	 * Plays the song at the specified row of the song table.
	 *
	 * @param row
	 *            The row that the song is located in
	 */
	public void play(int row) {
		try {
			// Have the playlist point to the appropriate song, then play it
			controller.getSongTable().getSelectionModel().clearAndSelect(row);
			controller.getPlayer().playSong(controller.getSongList().get(row));
			controller.getPlayer().setEndOfSongAction(controller::playNext);

			// Update the status bar accordingly
			controller.getStatus().setText("Now Playing: " + controller.getPlayer().getNowPlaying().toString());
		} catch (NullPointerException e) {
			if (controller.getSongList().isEmpty())
				LOGGER.info("The playlist is empty.");
			else
				LOGGER.info("The playlist is not empty.");
			LOGGER.error("Failed to play song.", e);
		} catch (Exception e) {
			LOGGER.error("Failed to play song.", e);
		}
	}

	/**
	 * Handling for the pause button. If the pause button says "Pause", it will
	 * pause the currently playing song, then change to "Resume". <br>
	 * <br>
	 * If it says "Resume", it will resume the currently playing song, then
	 * change to "Pause". <br>
	 * <br>
	 * If it says anything else, the error will be logged.
	 */
	public void pause() {
		if (controller.getPlayer().getNowPlaying() == null) {
			controller.getStatus().setText("No song is currently playing.");
			return;
		}

		if (controller.getMenuPause().getText().equals("Pause")) {
			controller.getStatus().setText("Paused: " + controller.getPlayer().getNowPlaying().toString());
			controller.getPlayer().pauseSong();
			controller.getShortcutPause().setText("Resume");
			controller.getMenuPause().setText("Resume");
		} else if (controller.getMenuPause().getText().equals("Resume")) {
			controller.getStatus().setText("Now Playing: " + controller.getPlayer().getNowPlaying().toString());
			controller.getPlayer().resumeSong();
			controller.getShortcutPause().setText("Pause");
			controller.getMenuPause().setText("Pause");
		} else {
			LOGGER.error("Invalid text for pause button detected, text was: " + controller.getMenuPause().getText());
			throw new AssertionError();
		}
	}

	/**
	 * Stops the currently playing song.
	 */
	public void stop() {
		if (controller.getPlayer().getNowPlaying() == null) {
			controller.getStatus().setText("No song is currently playing.");
			return;
		}

		controller.getStatus().setText("");
		controller.getShortcutPause().setText("Pause");
		controller.getMenuPause().setText("Pause");
		controller.getPlayer().stopSong();
	}

	/**
	 * Plays the previous song.
	 */
	public void playPrev() {
		if (controller.getPlayer().getNowPlaying() == null) {
			controller.getStatus().setText("No song is currently playing.");
			return;
		}

		int row = controller.getSongList().indexOf(controller.getPlayer().getNowPlaying());
		row = (row <= 0) ? 0 : row - 1;
		play(row);
		controller.getSongTable().getSelectionModel().select(row);
	}

	/**
	 * Plays the next song.
	 */
	public void playNext() {
		if (controller.getPlayer().getNowPlaying() == null) {
			controller.getStatus().setText("No song is currently playing.");
			return;
		}

		int row = controller.getSongList().indexOf(controller.getPlayer().getNowPlaying());
		row = (row + 1 >= controller.getSongList().size()) ? 0 : row + 1;
		play(row);
		controller.getSongTable().getSelectionModel().select(row);
	}

}

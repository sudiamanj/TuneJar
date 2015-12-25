package tunejar.menu;

import java.io.IOException;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import tunejar.player.Player;
import tunejar.player.PlayerController;
import tunejar.song.Playlist;

/**
 * Helper class for handling the File menu.
 */
public class FileMenu {

	private static final FileMenu INSTANCE = new FileMenu();
	private static final Logger LOGGER = LogManager.getLogger();

	private PlayerController controller;

	private FileMenu() {
		this.controller = PlayerController.getInstance();
	}

	/**
	 * Creates a new playlist.
	 * 
	 * @return The created playlist.
	 */
	public Playlist createPlaylist() {
		// Prompt the user for a playlist name.
		TextInputDialog dialog = new TextInputDialog("Untitled Playlist");
		dialog.setTitle("New Playlist");
		dialog.setHeaderText("Create a new playlist");
		dialog.setContentText("Playlist name:");

		Optional<String> playlistName = dialog.showAndWait();
		if (playlistName.isPresent()) {
			String pName = playlistName.get();

			// Playlist creation fails if a playlist with the specified name
			// already exists.
			for (Playlist p : controller.getPlaylistList()) {
				if (p.getName().equalsIgnoreCase(pName)) {
					Alert conflictAlert = new Alert(Alert.AlertType.WARNING);
					conflictAlert.setTitle("Playlist Conflict");
					conflictAlert.setHeaderText("A playlist named " + pName + " already exists.");
					conflictAlert.setContentText("Please rename/delete the existing playlist, or choose another name.");
					conflictAlert.showAndWait();
					return null;
				}
			}

			Playlist p = new Playlist(pName);
			try {
				p.save();
				PlaylistMenu.getInstance().loadPlaylist(p);
				return p;
			} catch (IOException e) {
				// Playlist creation fails if it cannot be successfully saved.
				Alert failAlert = new Alert(Alert.AlertType.ERROR);
				failAlert.setTitle("Playlist Write Error");
				failAlert.setHeaderText("Failed to create playlist: " + pName);
				failAlert.setContentText("The playlist failed to save. Make sure the name "
						+ "does not contain any illegal characters.");
				failAlert.showAndWait();
				LOGGER.error("Failed to save playlist: " + pName + ".m3u", e);
			}
		}
		return null;
	}

	/**
	 * Asks the user if it is okay to end the program. If so, end the program.
	 */
	public void quit() {
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setTitle("Exit JVMP3");
		alert.setHeaderText("Confirm Exit");
		alert.setContentText("Are you sure you would like to exit?");

		Optional<ButtonType> result = alert.showAndWait();
		if (result.get() == ButtonType.OK) {
			Platform.exit();
		}
	}

	public void addDirectory() {
		Player.getInstance().addDirectory();
		controller.getPlaylistList().set(0, Player.getInstance().getMasterPlaylist());
		controller.refreshTables();
		controller.focus(controller.getPlaylistTable(), 0);
	}

	public void removeDirectory() {
		if (Player.getInstance().removeDirectory()) {
			Player.getInstance().refresh();
			controller.getPlaylistList().set(0, Player.getInstance().getMasterPlaylist());
			controller.refreshTables();
			if (Player.getInstance().getNowPlaying() != null
					&& !Player.getInstance().getMasterPlaylist().contains(Player.getInstance().getNowPlaying())) {
				Player.getInstance().stopPlayback();
			}
		}
	}

	public static FileMenu getInstance() {
		return INSTANCE;
	}

}

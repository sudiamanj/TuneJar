package com.sudicode.tunejar.player;

import com.google.common.collect.HashMultiset;
import com.sudicode.tunejar.config.Defaults;
import com.sudicode.tunejar.config.Options;
import com.sudicode.tunejar.song.Playlist;
import com.sudicode.tunejar.song.Song;
import com.sudicode.tunejar.song.SongFactory;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.stage.DirectoryChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Main class.
 */
public class Player extends Application {

    // Static
    private static Player instance;
    private static final Logger logger = LoggerFactory.getLogger(Player.class);

    // GUI
    private MediaPlayer mediaPlayer;
    private Song nowPlaying;
    private Stage primaryStage;
    private Scene scene;
    private PlayerController controller;

    // Data
    private AtomicBoolean initialized;
    private Playlist masterPlaylist;
    private LinkedHashSet<File> directories;
    private Options options;
    private double mediaPlayerSpeed;

    /**
     * Starts the application.
     *
     * @param args The command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Starts the program.
     *
     * @param primaryStage The stage that will hold the interface
     */
    @Override
    public void start(Stage primaryStage) {
        try {
            init(primaryStage);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            exitWithAlert(e);
        }
    }

    /**
     * Handles program initialization.
     *
     * @param stage The stage that will hold the interface
     * @throws IOException Failed to load the FXML, or could not load/save a
     *                     file.
     */
    private void init(Stage stage) throws IOException {
        // Initialization.
        setInstance(this);
        setSpeed(1);
        setOptions(new Options(Defaults.PREFERENCES_NODE));

        // Load the FXML file and display the interface.
        primaryStage = stage;
        URL location = getClass().getResource(Defaults.PLAYER_FXML);
        FXMLLoader fxmlLoader = new FXMLLoader();
        Parent root = fxmlLoader.load(location.openStream());

        setScene(new Scene(root));
        setController(fxmlLoader.getController());
        String theme = Defaults.THEME_MAP.get(getOptions().getTheme());
        getScene().getStylesheets().add(theme);
        logger.debug("Loaded theme: " + theme);

        // Set scene
        primaryStage.setTitle("TuneJar");
        primaryStage.setScene(getScene());
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream(Defaults.ICON)));

        // Set dimensions
        if (SystemUtils.IS_OS_WINDOWS) {
            primaryStage.setMaximized(getOptions().isMaximized());
        } else {
            primaryStage.setMaximized(false); // Issues with maximizing on OS X
        }
        Rectangle2D screen = Screen.getPrimary().getVisualBounds();
        primaryStage.setMinWidth(Math.min(360, screen.getWidth()));
        primaryStage.setMinHeight(Math.min(240, screen.getHeight()));
        primaryStage.setWidth(Math.min(getOptions().getWindowWidth(), screen.getWidth()));
        primaryStage.setHeight(Math.min(getOptions().getWindowHeight(), screen.getHeight()));

        // Show stage
        logger.info("Initializing stage (Dimensions: {}x{})", primaryStage.getWidth(), primaryStage.getHeight());
        primaryStage.show();

        // Load the directories. If none are present, alert the user.
        directories = readDirectories();
        if (directories.isEmpty()) {
            showNoDirectoriesAlert();
        }
        writeDirectories();

        // Set the sort order.
        String[] sortBy = getOptions().getSortOrder();
        List<TableColumn<Song, ?>> sortOrder = new ArrayList<>();
        for (String s : sortBy) {
            switch (s) {
                case "title":
                    sortOrder.add(getController().getTitleColumn());
                    break;
                case "artist":
                    sortOrder.add(getController().getArtistColumn());
                    break;
                case "album":
                    sortOrder.add(getController().getAlbumColumn());
                    break;
                default:
                    break;
            }
        }
        getController().setSortOrder(sortOrder);

        // Save changes to window size / maximization
        Window window = getScene().getWindow();
        window.widthProperty().addListener((obs, oldV, newV) -> {
            if (!SystemUtils.IS_OS_WINDOWS || !primaryStage.isMaximized()) {
                getOptions().setWindowWidth(newV.doubleValue());
                logger.trace("Window resized to {}px in width", newV);
            }
        });
        window.heightProperty().addListener(((obs, oldV, newV) -> {
            if (!SystemUtils.IS_OS_WINDOWS || !primaryStage.isMaximized()) {
                getOptions().setWindowHeight(newV.doubleValue());
                logger.trace("Window resized to {}px in height", newV);
            }
        }));
        primaryStage.maximizedProperty().addListener((obs, oldV, newV) -> {
            getOptions().setMaximized(newV);
            logger.trace(newV ? "Window maximized" : "Window restored");
        });

        // Create and display a playlist containing all songs from each directory.
        refresh();
    }

    /**
     * Restart the program.
     */
    public void restart() {
        primaryStage.close();

        // Stop any playing songs
        stopSong();

        // Set variables to null
        instance = null;
        mediaPlayer = null;
        nowPlaying = null;
        scene = null;
        controller = null;
        initialized = null;
        masterPlaylist = null;
        directories = null;
        options = null;

        // Re-initialize
        try {
            init(primaryStage);
        } catch (Exception e) {
            exitWithAlert(e);
        }
    }

    /**
     * First, adds all music files that can be found in available directories to
     * the master playlist. Then loads all available playlists.
     */
    public void refresh() {
        Task<?> refresher = new Refresher();
        refresher.progressProperty().addListener((obs, oldVal, newVal) -> getController().getStatus()
                .setText(refresher.getMessage() + new DecimalFormat("#0%").format(newVal)));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<?> future = executor.submit(refresher);
        executor.shutdown();
        new Thread(() -> {
            try {
                future.get(Defaults.TIMEOUT, TimeUnit.SECONDS);
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
                logger.error(e.getMessage(), e);
                Platform.runLater(() -> getController().getStatus()
                        .setText("An error has occurred: " + e.getClass().getSimpleName()));
            }
        }).start();
    }

    /**
     * Inner class designed to handle expensive operations invoked by the
     * <code>refresh()</code> method.
     */
    private class Refresher extends Task<Void> {
        /**
         * The main task associated with the <code>refresh()</code> method. This
         * is an expensive call, so it is <b>not</b> recommended to run it on
         * the GUI thread.
         */
        @Override
        protected Void call() throws Exception {
            logger.info("Refresh call started.");
            long begin = System.nanoTime();

            refreshMasterPlaylist();
            List<Playlist> playlists = getPlaylists();

            // Refresh the view.
            Platform.runLater(() -> {
                if (!isInitialized()) {
                    getController().getPlaylistMenu().loadPlaylist(getMasterPlaylist());
                    playlists.forEach(getController().getPlaylistMenu()::loadPlaylist);
                    getController().getVolumeSlider().setValue(getOptions().getVolume());
                } else {
                    getController().getPlaylistList().set(0, getMasterPlaylist());
                }
                getController().refreshTables();
                getController().focus(getController().getPlaylistTable(), 0);
                getController().getStatus().setText("");
                setInitialized(true);
            });

            String elapsedSeconds = new DecimalFormat("0.000").format((System.nanoTime() - begin) / 1000000000.0);
            logger.info("Refresh call complete. Time elapsed: {}s", elapsedSeconds);
            return null;
        }

        /**
         * Clears the master playlist, then constructs a new one out of all
         * supported audio files found in the set of directories.
         *
         * @throws InterruptedException if the current thread was interrupted while waiting
         * @throws ExecutionException   if the computation threw an exception
         */
        private void refreshMasterPlaylist() throws InterruptedException, ExecutionException {
            setMasterPlaylist(new Playlist("All Music"));
            if (directories != null) {
                logger.info("Found directories: " + directories);
                logger.info("Populating the master playlist...");

                Collection<Future<Song>> sFutures = getFutures(directories);
                long workDone = 0;
                long max = sFutures.size();
                updateMessage("Updating songs... ");
                for (Future<Song> song : sFutures) {
                    getMasterPlaylist().add(song.get());
                    updateProgress(++workDone, max);
                }
            }
        }

        /**
         * Constructs playlists out of all m3u files found in the playlists
         * folder. The constructed playlists are then wrapped into a collection.
         *
         * @return The collection of constructed playlists.
         * @throws InterruptedException if the current thread was interrupted while waiting
         * @throws ExecutionException   if the computation threw an exception
         */
        private List<Playlist> getPlaylists() throws InterruptedException, ExecutionException {
            List<Playlist> playlists = new ArrayList<>();
            if (!isInitialized()) {
                List<Future<Playlist>> pFutures = new ArrayList<>();
                ExecutorService outerExec = Executors.newWorkStealingPool();

                // Iterate through each playlist.
                LinkedHashMap<String, String> lhm = getOptions().getPlaylists();
                for (Entry<String, String> nameToM3UString : lhm.entrySet()) {
                    pFutures.add(outerExec.submit(() -> createPlaylist(nameToM3UString)));
                }
                outerExec.shutdown();
                for (Future<Playlist> playlist : pFutures) {
                    playlists.add(playlist.get());
                }
            }
            return playlists;
        }

        /**
         * Creates a playlist out of an m3u file.
         *
         * @param nameToM3UString An {@link Entry} which maps playlist name to the contents of its respective M3U file.
         * @throws IOException          If an I/O error occurs
         * @throws InterruptedException if the current thread was interrupted while waiting
         * @throws ExecutionException   if the computation threw an exception
         */
        private Playlist createPlaylist(Entry<String, String> nameToM3UString) throws IOException, InterruptedException, ExecutionException {
            String name = nameToM3UString.getKey();
            String m3uString = nameToM3UString.getValue();

            Playlist playlist = new Playlist(name);
            Queue<Future<Song>> sFutures = new ArrayDeque<>();

            // Get each song, line by line.
            try (BufferedReader reader = new BufferedReader(new StringReader(m3uString))) {
                ExecutorService innerExec = Executors.newWorkStealingPool();
                for (String nextLine; (nextLine = reader.readLine()) != null; ) {
                    final String s = nextLine;
                    sFutures.add(innerExec.submit(() -> SongFactory.create(new File(s))));
                }
                innerExec.shutdown();
            }

            // Add each song to the playlist.
            long workDone = 0;
            long max = sFutures.size();
            for (Future<Song> song : sFutures) {
                updateMessage("Updating " + playlist.getName() + "...");
                Song s = song.get();
                playlist.add(s);
                logger.debug("Added song: {} to playlist: {}", s, playlist.getName());
                updateProgress(++workDone, max);
            }
            return playlist;
        }

    }

    // ------------------- Media Player Controls ------------------- //

    /**
     * Loads a song into the media player, then plays it.
     *
     * @param song The song to play
     */
    public void playSong(Song song) {
        if (getNowPlaying() != null) {
            stopSong();
        }
        setNowPlaying(song);
        String uriString = new File(song.getAbsoluteFilename()).toURI().toString();
        try {
            mediaPlayer = new MediaPlayer(new Media(uriString));
            logger.debug("Loaded song: " + uriString);
            setVolume(getController().getVolumeSlider().getValue());
            logger.info("Playing: " + getNowPlaying());

            mediaPlayer.currentTimeProperty().addListener((val, oldTime, newTime) -> {
                if (mediaPlayer == null) {
                    return;
                }

                // Current Time
                int ctMin = (int) newTime.toMinutes();
                int ctSec = (int) newTime.toSeconds();
                String ctMinSec = String.format("%01d:%02d", ctMin, ctSec - ctMin * 60);
                getController().getCurrentTime().setText(ctMinSec);

                // Total Duration
                int tdMin = (int) mediaPlayer.getMedia().getDuration().toMinutes();
                int tdSec = (int) mediaPlayer.getMedia().getDuration().toSeconds();
                String tdMinSec = String.format("%01d:%02d", tdMin, tdSec - tdMin * 60);
                getController().getTotalDuration().setText(tdMinSec);

                // Seek Bar
                getController().getSeekBar().setProgress(((double) ctSec / (double) tdSec));
            });

            // Allow user to seek using the seek bar
            EventHandler<MouseEvent> seeker = event -> {
                int tdSec = (int) mediaPlayer.getMedia().getDuration().toSeconds();
                double frac = event.getX() / getController().getSeekBar().getWidth();
                mediaPlayer.seek(Duration.seconds(tdSec * frac));
            };
            getController().getSeekBar().setOnMouseDragged(seeker);
            getController().getSeekBar().setOnMouseReleased(seeker);

            // Play the song
            mediaPlayer.setRate(getSpeed());
            mediaPlayer.play();
        } catch (MediaException e) {
            getController().getStatus().setText("Failed to play the song.");
            logger.error(e.getMessage(), e);
        }
    }

    /**
     * Resumes the media player.
     */
    public void resumeSong() {
        if (mediaPlayer != null && getNowPlaying() != null) {
            logger.info("Resuming: " + getNowPlaying());
            mediaPlayer.play();
        }
    }

    /**
     * Pauses the media player.
     */
    public void pauseSong() {
        if (mediaPlayer != null && getNowPlaying() != null) {
            logger.info("Pausing: " + getNowPlaying());
            mediaPlayer.pause();
        }
    }

    /**
     * Stops the media player.
     */
    public void stopSong() {
        if (mediaPlayer != null && getNowPlaying() != null) {
            logger.info("Stopping: " + getNowPlaying());
            mediaPlayer.stop();
        }
        setNowPlaying(null);
    }

    // ------------------- File Manipulation ------------------- //

    /**
     * Adds a user-selected directory to the directory collection.
     */
    public void addDirectory() {
        File directory = chooseDirectory(primaryStage);
        if (directory == null) {
            return;
        }
        directories.add(directory);
        try {
            writeDirectories();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Failed");
            alert.setHeaderText("Failed to add the directory.");
            alert.showAndWait();
            logger.error(e.getMessage(), e);
        }
        refresh();
    }

    /**
     * Allows the user to choose and remove a directory from the directory set.
     *
     * @return True iff a directory was successfully removed.
     */
    public boolean removeDirectory() {
        if (directories.isEmpty()) {
            getController().getStatus().setText("No folders found.");
            return false;
        }

        // Create and display dialog box.
        List<File> choices = new ArrayList<>();
        choices.addAll(directories);
        ChoiceDialog<File> dialog = new ChoiceDialog<>(choices.get(0), choices);
        dialog.setTitle("Remove Folder");
        dialog.setHeaderText("Which folder would you like to remove?");
        dialog.setContentText("Choose a folder:");
        Optional<File> result = dialog.showAndWait();

        // Remove the chosen folder unless the user pressed "cancel".
        if (result.isPresent()) {
            directories.remove(result.get());
            writeDirectories();
            getController().getStatus().setText("Directory removed.");
            logger.info("Directory removed. Remaining directories:" + directories);
            return true;
        }
        return false;
    }

    /**
     * Prompts the user for a directory.
     *
     * @param stage The stage that will hold the dialog box
     * @return The directory specified by the user, or null if the user cancels
     */
    private File chooseDirectory(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Where are your songs?");
        return chooser.showDialog(stage);
    }

    /**
     * Alert the user that no directories were found.
     */
    private void showNoDirectoriesAlert() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Empty Music Library");
        alert.setHeaderText(null);
        alert.setContentText("To add a music folder, click 'File' > 'Import' > 'Music Folder...'.");
        alert.showAndWait();
    }

    /**
     * Reads directories from the options file.
     *
     * @return A set containing the directories
     */
    private LinkedHashSet<File> readDirectories() {
        return getOptions().getDirectories();
    }

    /**
     * Writes directories to the options file.
     */
    private void writeDirectories() {
        getOptions().setDirectories(directories);
    }

    /**
     * Traverses each directory, obtaining all supported audio files. Each audio
     * file found is wrapped in a <code>Future&lt;Song&gt;</code>, which is then
     * added to a collection.
     *
     * @return A collection of type <code>Future&lt;Song&gt;</code>
     */
    private Collection<Future<Song>> getFutures(Collection<File> directories) {
        // Initialization
        Collection<Future<Song>> futures = HashMultiset.create();
        ExecutorService executor = Executors.newWorkStealingPool();

        // Loop through directories
        for (File directory : directories) {
            if (directory == null || !directory.isDirectory()) {
                logger.error("Failed to access directory: " + directory + ", skipping...");
                continue;
            }

            // Depth first search through each directory for supported files
            try (Stream<Path> str = Files.walk(directory.toPath())) {
                str.filter(path -> FilenameUtils.getExtension(path.toString()).matches("mp3|mp4|m4a|wav"))
                        .forEach(path -> futures.add(executor.submit(() -> SongFactory.create(path.toFile()))));
            } catch (IOException e) {
                logger.error("Failed to access directory: " + directory, e);
            }
        }
        executor.shutdown();

        return futures;
    }

    // ------------------- Exception Handling ------------------- //

    /**
     * Displays a dialog box explaining what happened. Once the dialog box is
     * closed, the program exits with exit code -1.
     *
     * @param e An exception that should end the program
     */
    private void exitWithAlert(Exception e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);

        // Store the stack trace in a string.
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);

        // Create an alert to let the user know what happened.
        alert.setTitle("Critical Error");
        alert.setHeaderText(null);
        alert.setContentText("A critical error has occured. The application will now terminate.");

        // Store the stack trace string in a textarea hidden by a "Show/Hide
        // Details" button.
        TextArea textArea = new TextArea(sw.toString());
        textArea.setEditable(false);
        textArea.setWrapText(false);
        textArea.setMaxWidth(Double.MAX_VALUE);
        textArea.setMaxHeight(Double.MAX_VALUE);

        GridPane.setVgrow(textArea, Priority.ALWAYS);
        GridPane.setHgrow(textArea, Priority.ALWAYS);
        GridPane gridPane = new GridPane();
        gridPane.setMaxWidth(Double.MAX_VALUE);
        gridPane.add(textArea, 0, 0);

        // Display the alert, then exit the program.
        alert.getDialogPane().setExpandableContent(gridPane);
        alert.showAndWait();
        Platform.exit();
    }

    // ------------------- Getters and Setters ------------------- //

    /**
     * Sets up the media player to perform a specified action at the end of
     * every song.
     *
     * @param action An action wrapped in a Runnable
     */
    public void setEndOfSongAction(Runnable action) {
        mediaPlayer.setOnEndOfMedia(action);
    }

    /**
     * @return The {@link Song} that is currently playing
     */
    public Song getNowPlaying() {
        return nowPlaying;
    }

    /**
     * @param nowPlaying The {@link Song} to set <code>nowPlaying</code> to
     */
    private void setNowPlaying(Song nowPlaying) {
        this.nowPlaying = nowPlaying;
    }

    /**
     * Adjust media volume.
     *
     * @param value Between [0.0, 1.0]
     */
    public void setVolume(double value) {
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(value);
        }
    }

    /**
     * @return <code>Playlist</code> containing all available music.
     */
    public Playlist getMasterPlaylist() {
        return masterPlaylist;
    }

    /**
     * @param masterPlaylist The {@link Playlist} to set <code>masterPlaylist</code> to
     */
    private void setMasterPlaylist(Playlist masterPlaylist) {
        this.masterPlaylist = masterPlaylist;
    }

    /**
     * Set the {@link Player} instance.
     *
     * @param instance An instance of {@link Player}
     */
    private static void setInstance(Player instance) {
        Player.instance = instance;
    }

    /**
     * Get the {@link Player} instance. Since {@link Application} can only be launched once, the instance should remain
     * consistent once initialized.
     *
     * @return Instance of {@link Player}
     */
    protected static Player getPlayer() {
        return instance;
    }

    /**
     * @return The {@link PlayerController} associated with this {@link Player}.
     */
    protected PlayerController getController() {
        return controller;
    }

    /**
     * @param controller The {@link PlayerController} to set <code>controller</code> to
     */
    private void setController(PlayerController controller) {
        this.controller = controller;
    }

    /**
     * @return The {@link Scene} associated with this {@link Player}.
     */
    public Scene getScene() {
        return scene;
    }

    /**
     * @param scene The {@link Scene} to set <code>scene</code> to
     */
    private void setScene(Scene scene) {
        this.scene = scene;
    }

    /**
     * Set the <code>initialized</code> state.
     *
     * @param initialized <code>true</code> if the application is initialized
     */
    private void setInitialized(boolean initialized) {
        if (this.initialized == null) {
            this.initialized = new AtomicBoolean(initialized);
        } else {
            this.initialized.set(initialized);
        }
    }

    /**
     * Get the <code>initialized</code> state.
     *
     * @return <code>true</code> if the application is initialized
     */
    public boolean isInitialized() {
        if (this.initialized == null) {
            this.initialized = new AtomicBoolean();
        }
        return initialized.get();
    }

    /**
     * @return The {@link Options} associated with this {@link Player}.
     */
    public Options getOptions() {
        return options;
    }

    /**
     * @param options The {@link Options} to set <code>options</code> to
     */
    private void setOptions(Options options) {
        this.options = options;
    }

    /**
     * Set playback speed.
     *
     * @param speed Between [0.0, 8.0]. (1.0 is normal speed, 2.0 is twice as fast, .5 is half as fast, etc)
     */
    public void setSpeed(double speed) {
        if (mediaPlayer != null) {
            mediaPlayer.setRate(speed);
        }
        this.mediaPlayerSpeed = speed;
    }

    /**
     * @return The playback speed (multiplier).
     */
    private double getSpeed() {
        return mediaPlayerSpeed;
    }

}

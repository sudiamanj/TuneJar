package tunejar.player;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.junit.BeforeClass;
import org.loadui.testfx.GuiTest;
import org.testfx.api.FxRobot;

import com.jayway.awaitility.Awaitility;

import javafx.application.Application;
import javafx.scene.Parent;
import tunejar.config.Options;

public abstract class PlayerTest {

	private static boolean initialized = false;
	private static GuiTest driver;
	private static FxRobot robot;

	/**
	 * Starts the TuneJar player.
	 * 
	 * @throws Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		if (!initialized)
			init();
	}

	/**
	 * Used to manipulate the TuneJar player.
	 * 
	 * @see GuiTest
	 */
	public static GuiTest getDriver() {
		return driver;
	}

	public static Player getPlayer() {
		return Player.getPlayer();
	}

	public static FxRobot getRobot() {
		return robot;
	}

	private static void init() throws Exception {
		// Initialization
		robot = new FxRobot();

		// Set directories
		Options options = new Options();
		Set<File> set = new HashSet<>();
		set.add(new File("src/test/resources/"));
		options.setDirectories(set);

		// Launch the application
		new Thread(() -> Application.launch(Player.class)).start();
		Awaitility.await().atMost(30, TimeUnit.SECONDS).until((Callable<Boolean>) () -> {
			try {
				return getPlayer().isInitialized();
			} catch (NullPointerException e) {
				return false;
			}
		});
		getRobot().sleep(1000);
		driver = new GuiTest() {
			@Override
			protected Parent getRootNode() {
				return Player.getPlayer().getScene().getRoot();
			}
		};
		initialized = true;
	}

}

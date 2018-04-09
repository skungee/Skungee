package me.limeglass.skungee.bungeecord;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.jdt.annotation.Nullable;

import me.limeglass.skungee.EncryptionUtil;
import me.limeglass.skungee.UniversalSkungee;
import me.limeglass.skungee.bungeecord.listeners.EventListener;
import me.limeglass.skungee.bungeecord.sockets.SocketRunnable;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginDescription;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public class Skungee extends Plugin {
	
	//Bungeecord
	
	private final static String prefix = "&8[&cSkungee&8] &e";
	private final static String nameplate = "[Skungee] ";
	private static EncryptionUtil encryption;
	private static BungecordMetrics metrics;
	private static Configuration config;
	private static File SCRIPTS_FOLDER;
	private ServerSocket serverSocket;
	private static Skungee instance;
	private static File configFile;
	
	public void onEnable(){
		instance = this;
		UniversalSkungee.setBungeecord(true);
		if (!getDataFolder().exists()) getDataFolder().mkdir();
		SCRIPTS_FOLDER = new File(getDataFolder(), File.separator + "scripts");
		if (!SCRIPTS_FOLDER.exists()) SCRIPTS_FOLDER.mkdir();
		Boolean newConfig = false;
		configFile = new File(getDataFolder(), "config.yml");
		if (!configFile.exists()) {
			try (InputStream in = getResourceAsStream("Bungeecord/config.yml")) {
				newConfig = true;
				Files.copy(in, configFile.toPath());
			} catch (IOException e) {
				exception(e, "could not create config.");
			}
		}
		try {
			config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
		} catch (IOException e) {
			exception(e, "could not save config.");
		}
		if (newConfig) consoleMessage("&cNo config was found, generating a new config...");
		if (!getDescription().getVersion().equals(config.getString("version"))) {
			consoleMessage("&eThere is a new Skungee version. Generating new config...");
			try (InputStream in = getResourceAsStream("Bungeecord/config.yml")) {
				Files.delete(configFile.toPath());
				Files.copy(in, configFile.toPath());
			} catch (IOException e) {
				exception(e, "could not create and save config due to new version.");
			}
		}
		encryption = new EncryptionUtil(this, false);
		encryption.hashFile();
		metrics = new BungecordMetrics(this);
		metrics.addCustomChart(new BungecordMetrics.SimplePie("amount_of_plugins") {
			@Override
			public String getValue() {
				return getProxy().getPluginManager().getPlugins().size() + "";
			}
		});
		/*metrics.addCustomChart(new BungecordMetrics.SingleLineChart("amount_of_network_variables") {
			@Override
			public int getValue() {
				return VariableStorage.getSize();
			}
		});*/
		metrics.addCustomChart(new BungecordMetrics.SingleLineChart("amount_of_global_scripts") {
			@Override
			public int getValue() {
				return SCRIPTS_FOLDER.listFiles().length;
			}
		});
		if (config.getBoolean("Events", false)) getProxy().getPluginManager().registerListener(this, new EventListener());
		connect();
		VariableStorage.setup();
		if (!config.getBoolean("DisableRegisteredInfo", false)) consoleMessage("has been enabled!");
	}
	
	private void connect () {
		try {
			serverSocket = new ServerSocket(getConfig().getInt("port", 1337), 69);
			consoleMessage("connection established on port " + getConfig().getInt("port", 1337));
			ProxyServer.getInstance().getScheduler().runAsync(getInstance(), new Runnable() {
				@Override
				public void run() {
					while (!serverSocket.isClosed()) {
						try {
							new Thread(new SocketRunnable(serverSocket.accept())).start();
						} catch (IOException e) {
							Skungee.exception(e, "Socket couldn't be accepted.");
						}
					}
				}
			});
		} catch (IOException e) {
			Skungee.exception(e, "ServerSocket couldn't be created on port: " + getConfig().getInt("port", 1337));
		}
	}
	
	@SuppressWarnings("deprecation")
	public final static void exception(Throwable cause, String... info) {
		Map<String, PluginDescription> plugins = new HashMap<String, PluginDescription>();
		for (Plugin plugin : ProxyServer.getInstance().getPluginManager().getPlugins()) {
			if (!plugin.getDescription().getName().equals("Skungee")) {
				String[] parts = plugin.getDescription().getMain().split("\\.");
				StringBuilder name = new StringBuilder(plugin.getDescription().getMain().length());
				for (int i = 0; i < parts.length - 1; i++) {
					name.append(parts[i]).append('.');
				}
				plugins.put(name.toString(), plugin.getDescription());
			}
		}
		infoMessage();
		infoMessage(getNameplate() + "Severe Error:");
		infoMessage(info);
		infoMessage();
		infoMessage("Something went wrong within Skungee.");
		infoMessage("Please report this error to the developers of Skungee so we can fix this from happening in the future.");
		infoMessage();
		Set<PluginDescription> stackPlugins = new HashSet<>();
		for (StackTraceElement stackTrace : Thread.currentThread().getStackTrace()) {
			for (Entry<String, PluginDescription> entry : plugins.entrySet()) {
				if (stackTrace.getClassName().contains(entry.getKey())) {
					stackPlugins.add(entry.getValue());
				}
			}
		}
		if (!stackPlugins.isEmpty()) {
			infoMessage("It looks like you are using some plugin(s) that aren't allowing Skungee to work properly.");
			infoMessage("Following plugins are probably related to this error in some way:");
			StringBuilder pluginsMessage = new StringBuilder();
			for (PluginDescription desc : stackPlugins) {
				pluginsMessage.append(desc.getName());
				pluginsMessage.append(" ");
			}
			infoMessage(pluginsMessage.toString());
			infoMessage("You should try disabling those plugins one by one, trying to find which one causes it.");
			infoMessage("If the error doesn't disappear even after disabling all listed plugins, it is probably a Skungee issue.");
		}
		infoMessage();
		infoMessage("Please report this on the Skungee discussion page on SkUnity or Spigot. You can also message one of the Skungee developers.");
		infoMessage();
		infoMessage("Stack trace:");
		boolean first = true;
		while (cause != null) {
			infoMessage((first ? "" : "Caused by: ") + cause.toString());
			for (final StackTraceElement e : cause.getStackTrace())
				infoMessage("    at " + e.toString());
			cause = cause.getCause();
			first = false;
		}
		infoMessage();
		infoMessage("Information:");
		infoMessage("  Skungee: " + getConfig().getString("version"));
		infoMessage("  Bungee: " + ProxyServer.getInstance().getVersion());
		infoMessage("  Game version: " + ProxyServer.getInstance().getGameVersion());
		infoMessage("  Protocol version: " + ProxyServer.getInstance().getProtocolVersion());
		infoMessage("  Java: " + System.getProperty("java.version") + " (" + System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version") + ")");
		infoMessage("  OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch") + " " + System.getProperty("os.version"));
		infoMessage();
		infoMessage("Thread: " + Thread.currentThread());
		infoMessage();
		infoMessage("End of Error.");
		infoMessage();
	}
	
	public static Skungee getInstance() {
		return instance;
	}
	
	public static Configuration getConfig() {
		return config;
	}
	
	public static BungecordMetrics getMetrics() {
		return metrics;
	}
	
	public static EncryptionUtil getEncrypter() {
		return encryption;
	}
	
	public static String getNameplate() {
		return nameplate;
	}
	
	public static String getPrefix() {
		return prefix;
	}
	
	public static File getScriptsFolder() {
		return SCRIPTS_FOLDER;
	}
	
	public static void debugMessage(String text) {
		if (config.getBoolean("debug")) consoleMessage("&b" + text);
	}
	
	public static String cc(String string) {
		return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes((char)'&', (String)string);
	}
	
	public static void infoMessage(@Nullable String... messages) {
		if (messages != null && messages.length > 0) {
			for (String text : messages) ProxyServer.getInstance().getLogger().info(getNameplate() + text);
		} else {
			ProxyServer.getInstance().getLogger().info("");
		}
	}

	public static void consoleMessage(@Nullable String... messages) {
		if (config.getBoolean("DisableConsoleMessages", false)) return;
		if (messages != null && messages.length > 0) {
			for (String text : messages) ProxyServer.getInstance().getLogger().info(cc(prefix + text));
		} else {
			ProxyServer.getInstance().getLogger().info("");
		}
	}
}
/*
Fixed some debug messages not having proper english statements, and also removed some development debug messages.

Fixed Conditions and Effects returning the modified syntax if the user has modified it in debug messages.

Fixed Expression CurrentServer from potentially returning the incorrect server due to two servers on the same server machine.

Fixed two expression where the ServerTracker could not understand servers that share a similar server machine.

Added a disconnect packet so the Bungeecord Skungee can tell when a server is disconnected quicker.

Added a port checker for the ServerTracker

Added configuration option to make

TODO:
Added title stuff:

	(Returns SkungeeTitle)
	[new] (skungee|bungee[[ ]cord]) title [with text] %string% [and] [with subtitle %-string%] [[that] lasts] for %timespan%[[,] [with] fade in %-timespan%][[,] [and] [with] fade out %-timespan%]
	
	(show|display|send) %skungeetitle% to bungee[[ ]cord]] [(player|uuid)][s] %strings/players%

Added SkungeeTitle type. This is a custom title object that works on Spigot and Bungeecord. Mainly used internally.

Added string of SkungeeTitle (Returns the main String of the title):

	Has all changers but ADD.
	[(all [[of] the]|the)] (message|string)[s] (of|from) [(skungee|bungee[[ ]cord])] title[s] %skungeetitles%

Added subtitle of SkungeeTitle (Returns the subtitle String):

	Has all changers but ADD.
	[(all [[of] the]|the)] sub[-]title[s] (of|from) [(skungee|bungee[[ ]cord])] title[s] %skungeetitles%
*/
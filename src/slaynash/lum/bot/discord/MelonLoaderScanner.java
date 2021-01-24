package slaynash.lum.bot.discord;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import slaynash.lum.bot.discord.logscanner.AudicaModDetails;
import slaynash.lum.bot.discord.logscanner.BTD6ModDetails;
import slaynash.lum.bot.discord.logscanner.ModDetails;
import slaynash.lum.bot.discord.logscanner.VRCModVersionDetails;
import slaynash.lum.bot.discord.logscanner.VRCModDetails;

public class MelonLoaderScanner {
	
	public static String latestMLVersionRelease = "0.2.7.4";
	public static String latestMLVersionBeta = "0.3.0";
	
	private static List<MelonLoaderError> knownErrors = new ArrayList<MelonLoaderError>() {{
		add(new MelonLoaderError(
				".*System\\.IO\\.FileNotFoundException\\: .* ['|\"]System\\.IO\\.Compression.*", 
				"You are actually missing the required .NET Framework for MelonLoader.\nPlease make sure to install it using the following link: <https://dotnet.microsoft.com/download/dotnet-framework/net48>"));
		add(new MelonLoaderError(
				"System.UnauthorizedAccessException:.*",
				"The access to a file has been denied. Please make sure the game is closed when installing MelonLoader, or try restarting your computer. If this doesn't works, try running the MelonLoader Installer with administrator privileges"));
		
		add(new MelonLoaderError(
				"\\[[0-9.:]+\\]    at MelonLoader\\.AssemblyGenerator\\.LocalConfig\\.Save\\(String path\\)",
				"The access to a file has been denied. Please try starting the game with administrator privileges, or try restarting your computer (failed to save AssemblyGenerator/config.cfg)"));
		add(new MelonLoaderError(
				"\\[[0-9.:]+\\]    at MelonLoader\\.AssemblyGenerator\\.Main\\.SetupDirectory\\(String path\\)",
				"The access to a file has been denied. Please try starting the game with administrator privileges, or try restarting your computer (failed to setup directories)"));
		add(new MelonLoaderError(
				"\\[[0-9.:]+\\] Il2CppDumper.exe does not exist!",
				"MelonLoader assembly generation failed. Please delete the `MelonLoader` folder and `version.dll` file from your game folder, and install MelonLoader again (failed to download Il2CppDumper)"));
		
		add(new MelonLoaderError(
				"\\[[0-9.:]+\\] \\[emmVRCLoader\\] You have emmVRC's Stealth Mode enabled..*",
				"You have emmVRC's Stealth Mode enabled. To access the functions menu, press the \"Report World\" button. Most visual functions of emmVRC have been disabled."));
		
		add(new MelonLoaderError(
				"\\[[0-9.:]+\\] \\[ERROR\\] System.BadImageFormatException:.*",
				"You have an invalid or incompatible assembly in your `Mods` or `Plugins` folder."));
		
		add(new MelonLoaderError(
				"\\[[0-9.:]+\\] \\[INTERNAL FAILURE\\] Failed to Execute Assembly Generator!",
				"The assembly generation failed. This is most likely caused by your anti-virus. Add an exception, or disable it, then try again."));
		
		/*
		add(new MelonLoaderError(
				".*Harmony\\.HarmonyInstance\\..*",
				"You seems to have a 0Harmony.dll file in your `Mods` or `Plugins` folder. This breaks mods and plugins, since Harmony is embed into MelonLoader"));
		*/
	}};
	
	private static Gson gson = new Gson();
	private static Map<String, List<ModDetails>> mods = new HashMap<>();
	private static Map<String, Boolean> checkUsingHashes = new HashMap<>() {{
		put("VRChat", false);
		put("BloonsTD6", false);
		put("Audica", false);
		
		put("BONEWORKS", true);
	}};
	
	private final static HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .build();
	
	private static Map<String, String> modNameMatcher = new HashMap<String, String>() {{
		// MelonInfo name -> Submitted name
	    put("Advanced Safety", "AdvancedSafety");
	    put("MControl", "MControl (Music Playback Controls)");
	    put("Player Volume Control", "PlayerVolumeControl");
	    put("UI Expansion Kit", "UIExpansionKit");
	    put("NearClipPlaneAdj", "NearClippingPlaneAdjuster.dll");
	    put("Particle and DynBone limiter settings UI", "ParticleAndBoneLimiterSettings");
	    put("MuteBlinkBeGone", "Mute Blink Be Gone");
	    put("DiscordRichPresence-ML", "VRCDiscordRichPresence-ML");
	    put("Core Limiter", "CoreLimiter");
	    put("MultiplayerDynamicBones", "Multiplayer Dynamic Bones");
	    put("Game Priority Changer", "GamePriority");
	    put("Runtime Graphics Settings", "RuntimeGraphicsSettings");
	    put("Advanced Invites", "AdvancedInvites");
	    put("No Steam. At all.", "NoSteamAtAll");
	    put("Rank Volume Control", "RankVolumeControl");
	    put("VRC Video Library", "VRCVideoLibrary");
	    put("Input System", "InputSystem");
	    put("TogglePostProcessing", "Toggle Post Processing");
	    put("ToggleMicIcon", "Toggle Mic Icon");
	    put("ThumbParams", "VRCThumbParams");
	    
	    // backward compatibility
	    put("BTKSANameplateFix", "BTKSANameplateMod");
	}};
	
	public static void Init() {
		
		Thread t = new Thread(() -> {
			System.out.println("MelonLoaderScannerThread start");
			while (true) {
				
				// VRChat
				
				HttpRequest request = HttpRequest.newBuilder()
	                .GET()
	                .uri(URI.create("http://client.ruby-core.com/api/mods.json"))
	                .setHeader("User-Agent", "LUM Bot")
	                .build();
				
				try {
					HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
					
					synchronized (mods) {
						List<VRCModDetails> vrcmods = gson.fromJson(response.body(), new TypeToken<ArrayList<VRCModDetails>>() {}.getType());
						
						List<ModDetails> modsprocessed = new ArrayList<>();
						for (VRCModDetails processingmods : vrcmods) {
							VRCModVersionDetails vrcmoddetails = processingmods.versions[0];
							modsprocessed.add(new ModDetails(vrcmoddetails.name, vrcmoddetails.modversion));
						}
						
						mods.put("VRChat", modsprocessed);
					}
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				}
				
				// BTD6
				
				request = HttpRequest.newBuilder()
		                .GET()
		                .uri(URI.create("https://raw.githubusercontent.com/Inferno-Dev-Team/Inferno-Omnia/main/version.json"))
		                .setHeader("User-Agent", "LUM Bot")
		                .build();
					
				try {
					HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
					
					synchronized (mods) {
						Map<String, BTD6ModDetails> processingmods = gson.fromJson(response.body(), new TypeToken<HashMap<String, BTD6ModDetails>>() {}.getType());
						
						List<ModDetails> modsprocessed = new ArrayList<>();
						for (Entry<String, BTD6ModDetails> mod : processingmods.entrySet()) {
							modsprocessed.add(new ModDetails(mod.getKey(), mod.getValue().version));
						}
						
						mods.put("BloonsTD6", modsprocessed);
					}
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				}
				
				// Audica
				
				request = HttpRequest.newBuilder()
		                .GET()
		                .uri(URI.create("https://raw.githubusercontent.com/Ahriana/AudicaModsDirectory/main/api.json"))
		                .setHeader("User-Agent", "LUM Bot")
		                .build();
					
				try {
					HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
					
					synchronized (mods) {
						Map<String, AudicaModDetails> processingmods = gson.fromJson(response.body(), new TypeToken<HashMap<String, AudicaModDetails>>() {}.getType());
						
						List<ModDetails> modsprocessed = new ArrayList<>();
						for (Entry<String, AudicaModDetails> mod : processingmods.entrySet()) {
							modsprocessed.add(new ModDetails(mod.getKey(), mod.getValue().version));
						}
						
						mods.put("Audica", modsprocessed);
					}
				} catch (IOException | InterruptedException e) {
					e.printStackTrace();
				}
				
				// BONEWORKS
				
				// TODO
				
				// Sleep
				
				try {
					Thread.sleep(6 * 60 * 1000); // 10 times / hour (every 6 minutes)
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			
		}, "MelonLoaderScannerThread");
		t.setDaemon(true);
		t.start();
	}

	public static void scanLogs(MessageReceivedEvent event) {
		List<Attachment> attachments = event.getMessage().getAttachments();
		
		List<MelonLoaderError> errors = new ArrayList<MelonLoaderError>();
		String mlVersion = null;
		boolean hasErrors = false;
		String game = null;
		String mlHashCode = null;

		boolean preListingMods = false;
		boolean listingMods = false;
		Map<String, LogsModDetails> loadedMods = new HashMap<String, LogsModDetails>();

		List<String> duplicatedMods = new ArrayList<String>();
		List<String> unverifiedMods = new ArrayList<String>();
		List<String> universalMods = new ArrayList<String>();
		List<String> incompatibleMods = new ArrayList<String>();
		List<MelonInvalidMod> invalidMods = new ArrayList<MelonInvalidMod>();
		Map<String, String> modAuthors = new HashMap<String, String>();
		
		List<String> modsThrowingErrors = new ArrayList<String>();
		
		String emmVRCVersion = null;
		String emmVRCVRChatBuild = null;
		
		boolean consoleCopyPaste = false;
		boolean pre3 = false;
		int remainingModCount = 0;
		
		String tmpModName = null, tmpModVersion = null, tmpModHash = null;
		
		for (int i = 0; i < attachments.size(); ++i) {
			Attachment attachment = attachments.get(i);
			
			if (attachment.getFileExtension().toLowerCase().equals("log") || attachment.getFileExtension().toLowerCase().equals("txt")) {
				try (BufferedReader br = new BufferedReader(new InputStreamReader(attachment.retrieveInputStream().get()))) {
					
					System.out.println("Reading file " + attachment.getFileName());
					//String lastModName = null;
					String line = "";
					String lastLine = null;
					while ((lastLine = line) != null && (line = br.readLine()) != null) {
						
						// Mod listing
						
						if (preListingMods || listingMods) {
							if (!pre3) {
								if (line.isEmpty()) {
									continue;
								}
								
								else if (preListingMods && line.matches("\\[[0-9.:]+\\] ------------------------------"));
								else if (preListingMods && (line.matches("\\[[0-9.:]+\\]( \\[MelonLoader\\]){0,1} No Plugins Loaded!") || line.matches("\\[[0-9.:]+\\]( \\[MelonLoader\\]){0,1} No Mods Loaded!"))) {
									preListingMods = false;
									listingMods = false;
									System.out.println("No mod/plugins loaded for this pass");
									
									continue;
								}
								else if (preListingMods && (line.matches("\\[[0-9.:]+\\]( \\[MelonLoader\\]){0,1} [0-9]+ Plugins Loaded") || line.matches("\\[[0-9.:]+\\]( \\[MelonLoader\\]){0,1} [0-9]+ Mods Loaded"))) {
									remainingModCount = Integer.parseInt(line.split(" ")[1]);
									preListingMods = false;
									listingMods = true;
									
									System.out.println(remainingModCount + " mods or plugins loaded on this pass");
									br.readLine(); // Skip line separator
									
									continue;
								}
								
								else if (listingMods && tmpModName == null) {
									String[] split = line.split(" ", 2)[1].split(" v", 2);
									tmpModName = split[0];
									tmpModVersion = split[1];
									continue;
								}
								else if (line.matches("\\[[0-9.:]+\\]( \\[MelonLoader\\]){0,1} by .*")) { // Skip author
									continue;
								}
								else if (line.matches("\\[[0-9.:]+\\]( \\[MelonLoader\\]){0,1} SHA256 Hash: [a-zA-Z0-9]+")) {
									tmpModHash = line.split(" ")[3];
									continue;
								}
								else if (line.matches("\\[[0-9.:]+\\]( \\[MelonLoader\\]){0,1} ------------------------------")) {

									System.out.println("Found mod " + tmpModName + ", version is " + tmpModVersion + ", and hash is " + tmpModHash);
									loadedMods.put(tmpModName, new LogsModDetails(tmpModVersion, tmpModHash));
									tmpModName = null;
									tmpModVersion = null;
									tmpModHash = null;
									
									--remainingModCount;
									
									if (remainingModCount == 0) {
										preListingMods = false;
										listingMods = false;
										System.out.println("Done scanning mods");
										
										continue;
									}
								}
							}
							
						}
						if (line.isEmpty());
						else if (line.matches("\\[[0-9.:]+\\]( \\[MelonLoader\\]){0,1} Using v0\\..*")) {
							if (line.matches("\\[[0-9.:]+\\] \\[MelonLoader\\] .*"))
								consoleCopyPaste = true;
							mlVersion = line.split("v")[1].split(" ")[0].trim();
							pre3 = true;
							System.out.println("ML " + mlVersion + " (< 0.3.0)");
						}
						else if (line.matches("\\[[0-9.:]+\\]( \\[MelonLoader\\]){0,1} MelonLoader v0\\..*")) {
							if (line.matches("\\[[0-9.:]+\\] \\[MelonLoader\\] .*"))
								consoleCopyPaste = true;
							mlVersion = line.split("v")[1].split(" ")[0].trim();
							System.out.println("ML " + mlVersion + " (>= 0.3.0)");
						}
						else if (line.matches("\\[[0-9.:]+\\]( \\[MelonLoader\\]){0,1} Name: .*")) {
							game = line.split(":", 4)[3].trim();
							System.out.println("Game: " + game);
						}
						else if (line.matches("\\[[0-9.:]+\\]( \\[MelonLoader\\]){0,1} Hash Code: .*")) {
							mlHashCode = line.split(":", 4)[3].trim();
							System.out.println("Hash Code: " + mlHashCode);
						}
						else if (line.matches("\\[[0-9.:]+\\]( \\[MelonLoader\\]){0,1} Game Compatibility: .*")) {
							String modnameversionauthor = lastLine.split("\\[[0-9.:]+\\]( \\[MelonLoader\\]){0,1} ", 2)[1].split("\\((http[s]{0,1}:\\/\\/){0,1}[a-zA-Z0-9\\-]+\\.[a-zA-Z]{2,4}", 2)[0];
							String[] split2 = modnameversionauthor.split(" by ", 2);
							String author = split2.length > 1 ? split2[1] : null;
							String[] split3 = split2[0].split(" v", 2);
							String name = split3[0].isBlank() ? "" : split3[0];
							name = String.join("", name.split(".*[a-zA-Z0-9]\\.[a-zA-Z]{2,4}"));
							String version = split3.length > 1 ? split3[1] : null;
							
							if (loadedMods.containsKey(name) && !duplicatedMods.contains(name))
								duplicatedMods.add(name.trim());
							loadedMods.put(name.trim(), new LogsModDetails(version, null));
							if (author != null)
								modAuthors.put(name.trim(), author.trim());
							
							String compatibility = line.split("\\[[0-9.:]+\\]( \\[MelonLoader\\]){0,1} Game Compatibility: ", 2)[1];
							if (compatibility.equals("Universal"))
								universalMods.add(name);
							else if (compatibility.equals("Compatible")) {}
							else
								incompatibleMods.add(name);

							System.out.println("Found mod " + name.trim() + ", version is " + version + ", compatibility is " + compatibility);
						}
						// VRChat / EmmVRC Specifics
						else if (mlVersion == null && line.matches("\\[[0-9.:]+\\] \\[emmVRCLoader\\] VRChat build is: .*")) {
							emmVRCVRChatBuild = line.split(":", 4)[3].trim();
							System.out.println("VRChat " + emmVRCVRChatBuild);
						}
						else if (mlVersion == null && line.matches("\\[[0-9.:]+\\] \\[emmVRCLoader\\] You are running version .*")) {
							emmVRCVersion = line.split("version", 2)[1].trim();
							System.out.println("EmmVRC " + emmVRCVersion);
						}
						else if (!pre3 && (line.matches("\\[[0-9.:]+\\] Loading Plugins...") || line.matches("\\[[0-9.:]+\\] Loading Mods..."))) {
							preListingMods = true;
							System.out.println("Starting to pre-list mods/plugins");
						}
						//
						else {
							boolean found = false;
							for (MelonLoaderError knownError : knownErrors) {
								if (line.matches(knownError.regex)) {
									if (!errors.contains(knownError))
										errors.add(knownError);
									System.out.println("Found known error");
									hasErrors = true;
									found = true;
									break;
								}
							}
							if (!found) {
								if (line.matches("\\[[0-9.:]+\\]( \\[MelonLoader\\]){0,1} \\[[^\\[]+\\] \\[(Error|ERROR)\\].*") && !line.matches("\\[[0-9.:]+\\] \\[MelonLoader\\] \\[(Error|ERROR)\\].*")) {
									String mod = line.split("\\[[0-9.:]+\\]( \\[MelonLoader\\]){0,1} \\[", 2)[1].split("\\]", 2)[0];
									if (!modsThrowingErrors.contains(mod))
										modsThrowingErrors.add(mod);
									System.out.println("Found mod error, caused by " + mod + ": " + line);
									hasErrors = true;
								}
								else if (line.matches("\\[[0-9.:]+\\]( \\[MelonLoader\\]){0,1} \\[(Error|ERROR)\\].*")) {
									hasErrors = true;
									System.out.println("Found non-mod error: " + line);
								}
							}
						}
					}
					
				} catch (InterruptedException | ExecutionException | IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		boolean isMLOutdated = mlVersion != null && !(mlVersion.equals(latestMLVersionRelease) || mlVersion.equals(latestMLVersionBeta));
		
		List<ModDetails> modDetails = null;
		
		if (game != null)
			modDetails = mods.get(game);
		
		boolean checkUsingHash = false;
		boolean hasMLHashes = false;
		
		if (modDetails != null) {
			
			checkUsingHash = checkUsingHashes.get(game);
			
			hasMLHashes = loadedMods.values().size() > 0 ? loadedMods.values().toArray(new LogsModDetails[0])[0].hash != null : checkUsingHash;
			
			if (checkUsingHash ? hasMLHashes : true) {
				for (Entry<String, LogsModDetails> entry : loadedMods.entrySet()) {
					String modName = entry.getKey();
					LogsModDetails logsModDetails = entry.getValue();
					String modVersion = logsModDetails.version;
					String modHash = logsModDetails.hash;
					
					if (modVersion == null) {
						unverifiedMods.add(modName);
						continue;
					}
					
					if (modVersion.startsWith("v"))
						modVersion = modVersion.substring(1);
					if (modVersion.split("\\.").length == 2)
						modVersion += ".0";
					
					String matchedModName = modNameMatcher.get(entry.getKey().trim());
					if (matchedModName != null) {
						modAuthors.put(matchedModName, modAuthors.get(modName));
					}
					
					String latestModVersion = null;
					String latestModHash = null;
					for (ModDetails modDetail : modDetails) {
						if (modDetail.name.replace(" ", "").equals(modName.replace(" ", ""))) {
							if (checkUsingHash) {
								// TODO
							}
							else {
								System.out.println("Mod found in db: " + modDetail.name + " version " + modDetail.versions[0].version);
								latestModVersion = modDetail.versions[0].version;
								if (latestModVersion.startsWith("v"))
									latestModVersion = latestModVersion.substring(1);
								if (latestModVersion.split("\\.").length == 2)
									latestModVersion += ".0";
								break;
							}
						}
					}
					
					if (latestModVersion == null && latestModHash == null) {
						unverifiedMods.add(modName);
					}
					else if (!checkUsingHash ? !modVersion.equals(latestModVersion) : (modHash != null && !modHash.equals(latestModHash))) {
						invalidMods.add(new MelonInvalidMod(modName, modVersion, latestModVersion));
					}
					/* TODO
					else if (modName.equals("emmVRC")) {
						if (emmVRCVersion == null) {
							
						}
						else {
							
						}
					}
					*/
				}
			}
		}
		
		if (mlHashCode != null) {
			boolean found = false;
			for (String code : CommandManager.melonLoaderHashes) {
				if (mlHashCode.equals(code)) {
					found = true;
					break;
				}
			}
			
			if (!found) {
				reportUserModifiedML(event);
			}
		}
		
		String message = "";
		
		if (consoleCopyPaste)
			message += "*You sent a copy of the console logs. Please type `!logs` to know where to find the complete game logs.*\n";
		
		if (game != null && !latestMLVersionBeta.equals(latestMLVersionRelease) && mlVersion.equals(latestMLVersionBeta))
			message += "*You are running an alpha version of MelonLoader.*\n";
		
		if (game != null && checkUsingHash && !hasMLHashes)
			message += "*Your MelonLoader doesn't provide mod hashes (requires >0.3.0). Mod versions will not be verified.*\n";
		else if (game != null && modDetails == null)
			message += "*" + game + " isn't officially supported by the autochecker. Mod versions will not be verified.*\n";
		
		if (errors.size() > 0 || isMLOutdated || duplicatedMods.size() != 0 || unverifiedMods.size() != 0 || invalidMods.size() != 0 || incompatibleMods.size() != 0 || modsThrowingErrors.size() != 0 || (mlVersion != null && loadedMods.size() == 0)) {
			message += "**MelonLoader log autocheck:** The autocheck reported the following problems <@" + event.getAuthor().getId() + ">:";
			
			if (isMLOutdated)
				message += "\n - The installed MelonLoader is outdated. Installed: **v" + sanitizeInputString(mlVersion) + "**. Latest: **v" + latestMLVersionRelease + "**";
			
			if ((mlVersion != null && loadedMods.size() == 0))
				message += "\n - You have no mods installed in your Mods and Plugins folder";
			
			if (duplicatedMods.size() > 0) {
				String error = "\n - The following mods are installed multiple times in your Mods and/or Plugins folder:";
				for (String s : duplicatedMods)
					error += "\n   \\> " + sanitizeInputString(s);
				message += error;
			}
			
			if (incompatibleMods.size() > 0) {
				String error = "\n - You are using the following incompatible mods:";
				for (String s : incompatibleMods)
					error += "\n   \\> " + sanitizeInputString(s);
				message += error;
			}
			
			if (unverifiedMods.size() > 0) {
				String error = "\n - You are using the following unverified/unknown mods:";
				for (String s : unverifiedMods)
					error += "\n   \\> " + sanitizeInputString(s) + (modAuthors.containsKey(s) ? (" **by** " + sanitizeInputString(modAuthors.get(s))) : "");
				message += error;
			}
			
			if (invalidMods.size() > 0) {
				String error = "\n - You are using the following outdated mods:";
				for (MelonInvalidMod m : invalidMods)
					error += "\n   \\> " + m.name + " - installed: `" + sanitizeInputString(m.currentVersion) + "`, latest: `" + m.latestVersion + "`";
				message += error;
			}
			
			
			for (int i = 0; i < errors.size(); ++i)
				message += "\n - " + sanitizeInputString(errors.get(i).error);
			
			
			if (modsThrowingErrors.size() > 0) {
				String error = "\n - The following mods are throwing errors:";
				for (String s : modsThrowingErrors)
					error += "\n   \\> " + sanitizeInputString(s);
				message += error;
			}
			
			event.getChannel().sendMessage(message).queue();
		}
		else if (mlVersion != null) {
			if (hasErrors) {
				event.getChannel().sendMessage(message + "**MelonLoader log autocheck:** The autocheck found some unknown problems in your logs. Please wait for a moderator or an helper to manually check the file").queue();
			}
			else
				event.getChannel().sendMessage(message + "**MelonLoader log autocheck:** The autocheck completed without finding any problem. Please wait for a moderator or an helper to manually check the file").queue();
		}
	}
	
	private static void reportUserModifiedML(MessageReceivedEvent event) {
		String reportChannel = CommandManager.mlReportChannels.get(event.getGuild().getIdLong()); // https://discord.com/channels/663449315876012052/663461849102286849/801676270974795787
		if (reportChannel != null) {
			event.getGuild().getTextChannelById(reportChannel).sendMessage(
					JDAManager.wrapMessageInEmbed(
							"User <@" + event.getMember().getId() + "> is using a modified MelonLoader.\nMessage: <https://discord.com/channels/" + event.getGuild().getId() + "/" + event.getChannel().getId() + "/" + event.getMessageId() + ">",
							Color.RED)).queue();
		}
	}

	private static String sanitizeInputString(String input) {
		return input
				.replace("@", "@ ")
				.replace("*", "\\*")
				.replace("`", "\\`")
				.replace("nigger", "[CENSORED]")
				.replace("nigga", "[CENSORED]");
	}
	
	private static class MelonLoaderError {
		String regex;
		String error;
		
		public MelonLoaderError(String regex, String error) {
			this.regex = regex;
			this.error = error;
		}
	}
	
	public static class MelonInvalidMod {
		String name;
		String currentVersion;
		String latestVersion;
		
		public MelonInvalidMod(String name, String currentVersion, String latestVersion) {
			this.name = name;
			this.currentVersion = currentVersion;
			this.latestVersion = latestVersion;
		}
	}
}

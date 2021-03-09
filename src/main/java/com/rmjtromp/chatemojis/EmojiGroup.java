package com.rmjtromp.chatemojis;

import static com.rmjtromp.chatemojis.ChatEmojis.NAME_PATTERN;
import static com.rmjtromp.chatemojis.ChatEmojis.RESERVED_NAMES;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;

import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import com.rmjtromp.chatemojis.ChatEmojis.AbstractEmoji;
import com.rmjtromp.chatemojis.exceptions.ConfigException;
import com.rmjtromp.chatemojis.utils.Lang;
import com.rmjtromp.chatemojis.utils.Lang.Replacements;

import net.md_5.bungee.api.chat.TextComponent;
import org.jetbrains.annotations.NotNull;

class EmojiGroup implements AbstractEmoji {
	
    private final List<Emoji> emojis = new ArrayList<>();
    private final List<EmojiGroup> groups = new ArrayList<>();

    private final String name;
    private final Permission permission;
    private final EmojiGroup parent;

    final List<String> parentNames = new ArrayList<>();

    static EmojiGroup init(@NotNull ConfigurationSection section) throws ConfigException {
        return new EmojiGroup(null, Objects.requireNonNull(section));
    }

    /**
     * Creates an {@link EmojiGroup} from the {@link ConfigurationSection} provided
     * @param parent The parent group of the EmojiGroup
     * @param section The {@link ConfigurationSection} where the EmojiGroup's information lies
     * @throws ConfigException If a configuration mistake is present, a configuration exception is thrown
     */
    private EmojiGroup(EmojiGroup parent, @NotNull ConfigurationSection section) throws ConfigException {
        this.parent = parent;
        String permissionBase = "chatemojis.use";
        if(parent != null) {
        	// scrape name from the ConfigurationSection path
            Matcher matcher = NAME_PATTERN.matcher(section.getCurrentPath() != null ? section.getCurrentPath() : "");
            if(matcher.find()) {
                name = matcher.group(1).replaceAll("[_\\s]+", "-").replaceAll("[^0-9a-zA-Z-]", "");
                if(name.isEmpty()) throw new ConfigException(Lang.translate("error.emojigroup.name.empty"), section);
            } else throw new ConfigException(Lang.translate("error.emojigroup.name.invalid"), section);

            // build the permission node base
            parentNames.add(this.name);
            EmojiGroup emoji = this;
            while(emoji.parent != null && emoji.parent.name != null) {
                parentNames.add(emoji.parent.name);
                emoji = emoji.parent;
            }
            Collections.reverse(parentNames);

            permissionBase = String.format("%s.%s", permissionBase, String.join(".", parentNames).toLowerCase());
        } else name = null;
        
        /*
         * Set the permission node, inherit name of parent groups.
         */
        permission = new Permission(String.format("%s.*", permissionBase));
        permission.setDefault(PermissionDefault.OP);
        permission.setDescription(String.format("Permission to use all emojis listed in '%s'", name));
        if(parent != null) permission.addParent(parent.getPermission(), true);
        
        /*
         * Loops through all keys and looks for anything that is not reserved
         * in the case of the first group (default group) "settings" is also reserved
         */
        for(String key : section.getKeys(false)) {
            if(!RESERVED_NAMES.contains(key.toLowerCase())) {
                ConfigurationSection s = section.getConfigurationSection(key);
                if(s == null) continue;
                if(isSetKey(s, "^emoticons?$") && isSetKey(s, "^emojis?$")) {
                    try {
                        emojis.add(Emoji.init(this, s));
                    } catch (ConfigException e) {
                    	Replacements replacements = new Replacements();
                    	replacements.add("emoji", key);
                    	replacements.add("message", e.getMessage());
                        System.out.println("[ChatEmoji] " + Lang.translate("error.load.emoji", replacements));
                    }
                } else {
                    try {
                    	groups.add(new EmojiGroup(this, s));
                    } catch (ConfigException e) {
                    	Replacements replacements = new Replacements();
                    	replacements.add("emojigroup", key);
                    	replacements.add("message", e.getMessage());
                        System.out.println("[ChatEmoji] " + Lang.translate("error.load.group", replacements));
                    }
                }
            }
        }
    }

    /**
     * Returns the name of the {@link EmojiGroup}
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Returns the {@link Permission} node of the {@link EmojiGroup}
     */
    @Override
    public Permission getPermission() {
        return permission;
    }

    /**
     * Returns all child {@link EmojiGroup}s
     */
    public List<EmojiGroup> getGroups() {
        return groups;
    }

    /**
     * Returms all child {@link Emoji}
     */
    public List<Emoji> getEmojis() {
        return emojis;
    }

    /**
     * Parses a string, replaces all emoticons with emojis.
     * Does this for all sub-groups and child emojis
     * @param player The player which its being parsed for
     * @param resetColor The default color it should go to after the emoji is inserted
     * @param message The message which should be parsed
     */
    String parse(@NotNull Player player, @NotNull String resetColor, @NotNull String message) {
        return parse(player, resetColor, message, false);
    }
    
    /**
     * Parses a string, replaces all emoticons with emojis.
     * Does this for all sub-groups and child emojis
     * @param player The player which its being parsed for
     * @param resetColor The default color it should go to after the emoji is inserted
     * @param message The message which should be parsed
     * @param forced Whether or not player permissions should be ignored
     */
    String parse(@NotNull Player player, @NotNull String resetColor, @NotNull String message, boolean forced) {
        for(EmojiGroup group : getGroups()) message = group.parse(player, resetColor, message, forced);
        for(Emoji emoji : getEmojis()) message = emoji.parse(player, resetColor, message, forced);
        return message;

    }

    /**
     * Returns {@link TextComponent} array to display in
     * list for each emoticons.
     * @param player The player which the emoticons should be parsed for
     */
    List<BaseComponent[]> getComponents(@NotNull Player player) {
        List<BaseComponent[]> components = new ArrayList<>();
        getGroups().forEach(group -> components.addAll(group.getComponents(player)));
        getEmojis().forEach(emoji -> components.addAll(emoji.getComponent(player)));
        return components;
    }

    /**
     * Checks if key exists in {@link ConfigurationSection} that matches the regular expression
     * @param section The configuration where it should be looking for
     * @param regex The regular expression
     */
    private boolean isSetKey(@NotNull ConfigurationSection section, @NotNull String regex) {
        for(String key : section.getKeys(false)) {
            if(key.toLowerCase().matches(regex)) return true;
        }
        return false;
    }

	public void forEach(@NotNull Consumer<AbstractEmoji> action) {
		groups.forEach(action);
		emojis.forEach(action);
	}

}

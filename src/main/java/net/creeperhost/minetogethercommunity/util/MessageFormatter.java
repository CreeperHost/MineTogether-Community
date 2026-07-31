package net.creeperhost.minetogethercommunity.util;

import net.creeperhost.minetogether.lib.chat.message.Message;
import net.creeperhost.minetogether.lib.chat.message.MessageComponent;
import net.creeperhost.minetogether.lib.chat.message.ProfileMessageComponent;
import net.creeperhost.minetogether.lib.chat.profile.Profile;
import net.creeperhost.minetogethercommunity.chat.ChatTarget;
import net.creeperhost.minetogethercommunity.chat.MineTogetherChat;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.util.text.event.HoverEvent;

import java.net.URI;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MessageFormatter {

    public static final String CLICK_NAME = "CE:CLICK_NAME";
    public static final String CLICK_MESSAGE = "CE:CLICK_MESSAGE";

    private static final Pattern URL_PATTERN = Pattern.compile(
            "((?:[a-z0-9]{2,}:\\/\\/)?(?:(?:[0-9]{1,3}\\.){3}[0-9]{1,3}|(?:[-\\w_]+\\.[a-z]{2,}?))(?::[0-9]{1,5})?.*?(?=[!\"\\u00A7 \\n]|$))",
            Pattern.CASE_INSENSITIVE);

    private MessageFormatter() {
    }

    public static ITextComponent formatInGame(ChatTarget target, Message message) {
        return formatInGame(target, message, -1);
    }

    public static ITextComponent formatInGame(ChatTarget target, Message message, int messageId) {
        ITextComponent root = new TextComponentString("");
        int clickMessageId = messageClickId(message, messageId);
        root.appendSibling(styled("<", arrowColor(message)));
        root.appendSibling(sender(message, messageId));
        root.appendSibling(styled("> ", arrowColor(message)));
        root.appendSibling(formatBody(message.getMessage(), messageColor(message), clickMessageId));
        return root;
    }

    public static ITextComponent formatGui(Message message) {
        ITextComponent root = new TextComponentString("");
        root.appendSibling(styled("<", arrowColor(message)));
        root.appendSibling(sender(message, -1));
        root.appendSibling(styled("> ", arrowColor(message)));
        root.appendSibling(formatBody(message.getMessage(), messageColor(message)));
        return root;
    }

    private static ITextComponent formatBody(MessageComponent component, TextFormatting color) {
        return formatBody(component, color, -1);
    }

    private static ITextComponent formatBody(MessageComponent component, TextFormatting color, int messageId) {
        if (component == null) {
            return formatBody("", color, messageId);
        }

        ITextComponent root = new TextComponentString("");
        for (MessageComponent child : component.iterate()) {
            TextFormatting childColor = color;
            if (child instanceof ProfileMessageComponent) {
                ProfileMessageComponent profileComponent = (ProfileMessageComponent) child;
                if (profileComponent.profile == MineTogetherChat.getOurProfile()) {
                    childColor = TextFormatting.RED;
                }
            }
            root.appendSibling(formatBody(child.getMessage(), childColor, messageId));
        }
        return root;
    }

    private static ITextComponent formatBody(String text, TextFormatting color) {
        return formatBody(text, color, -1);
    }

    private static ITextComponent formatBody(String text, TextFormatting color, int messageId) {
        ITextComponent root = new TextComponentString("");
        if (text == null || text.isEmpty()) {
            return root;
        }

        Matcher matcher = URL_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                root.appendSibling(styled(text.substring(lastEnd, matcher.start()), color, messageId));
            }

            String matchedUrl = matcher.group();
            String rawUrl = trimTrailingPunctuation(matchedUrl);
            String trailing = matchedUrl.substring(rawUrl.length());
            String openUrl = withScheme(rawUrl);
            if (openUrl == null) {
                root.appendSibling(styled(rawUrl, color, messageId));
            } else {
                root.appendSibling(link(rawUrl, openUrl));
            }
            if (!trailing.isEmpty()) {
                root.appendSibling(styled(trailing, color, messageId));
            }
            lastEnd = matcher.start() + matcher.group().length();
        }

        if (lastEnd < text.length()) {
            root.appendSibling(styled(text.substring(lastEnd), color, messageId));
        }
        return root;
    }

    private static ITextComponent link(String label, String url) {
        TextComponentString component = new TextComponentString(label);
        component.setStyle(new Style()
                .setColor(TextFormatting.BLUE)
                .setUnderlined(true)
                .setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                .setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(url))));
        return component;
    }

    private static ITextComponent sender(Message message, int messageId) {
        String name = senderName(message);
        TextComponentString component = new TextComponentString(name);
        Style style = new Style().setColor(userColor(message));
        if (message.sender != null && message.sender != MineTogetherChat.getOurProfile()) {
            style.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, name));
            style.setInsertion(messageId >= 0 ? CLICK_NAME + ":" + messageId : CLICK_NAME);
            style.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(name)));
        }
        component.setStyle(style);
        return component;
    }

    public static URL firstUrl(Message message) {
        if (message == null || message.getMessage() == null) return null;
        return firstUrl(message.getMessage().getMessage());
    }

    public static URL firstUrl(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher matcher = URL_PATTERN.matcher(text);
        if (!matcher.find()) return null;
        String openUrl = withScheme(trimTrailingPunctuation(matcher.group()));
        if (openUrl == null) return null;
        try {
            return new URL(openUrl);
        } catch (MalformedURLException ignored) {
            return null;
        }
    }

    private static ITextComponent styled(String text, TextFormatting color) {
        return styled(text, color, -1);
    }

    private static ITextComponent styled(String text, TextFormatting color, int messageId) {
        TextComponentString component = new TextComponentString(text);
        Style style = new Style().setColor(color);
        if (messageId >= 0) {
            style.setInsertion(CLICK_MESSAGE + ":" + messageId);
        }
        component.setStyle(style);
        return component;
    }

    private static int messageClickId(Message message, int messageId) {
        return messageId >= 0 && message.sender != null && message.sender != MineTogetherChat.getOurProfile() ? messageId : -1;
    }

    private static String withScheme(String url) {
        try {
            URI uri = new URI(url);
            return uri.getScheme() == null ? "http://" + url : url;
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private static String trimTrailingPunctuation(String value) {
        while (value.endsWith(".") || value.endsWith(",") || value.endsWith(";") || value.endsWith(":")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String senderName(Message message) {
        if (message.senderName != null) return message.senderName.getMessage();
        String name = MineTogetherChat.displayName(message.sender);
        return name == null || name.trim().isEmpty() ? "MineTogether" : name;
    }

    private static TextFormatting messageColor(Message message) {
        Profile sender = message.sender;
        if (sender != null) {
            if (sender.isBanned()) return TextFormatting.DARK_GRAY;
            if (sender == MineTogetherChat.getOurProfile()) return TextFormatting.GRAY;
        }
        return TextFormatting.WHITE;
    }

    private static TextFormatting arrowColor(Message message) {
        Profile sender = message.sender;
        if (sender != null) {
            if (sender.isPremium()) return TextFormatting.GREEN;
            if (sender == MineTogetherChat.getOurProfile()) return TextFormatting.GRAY;
        }
        return TextFormatting.WHITE;
    }

    private static TextFormatting userColor(Message message) {
        Profile sender = message.sender;
        if (sender == null) return TextFormatting.AQUA;
        Profile ours = MineTogetherChat.getOurProfile();
        if (sender.isFriend()) return isOnSamePack(ours, sender) ? TextFormatting.GOLD : TextFormatting.YELLOW;
        if (sender == ours) return TextFormatting.GRAY;
        if (isOnSamePack(ours, sender)) return TextFormatting.DARK_PURPLE;
        return TextFormatting.WHITE;
    }

    private static boolean isOnSamePack(Profile ours, Profile sender) {
        try {
            return ours != null && ours.isOnSamePack(sender);
        } catch (Throwable ignored) {
            return false;
        }
    }
}

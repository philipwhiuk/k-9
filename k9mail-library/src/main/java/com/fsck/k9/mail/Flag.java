
package com.fsck.k9.mail;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Flags that can be applied to Messages.
 *
 * Update: to support IMAP keywords ( custom flags ) this enum became
 * a class. This class was constructed to resemble enums. Since Java
 * enums are objects internally anyway ( implemented similar to these )
 * this will not be noticably slower.
 *
 * The extra field bCustom denotes custom flags. These get an prefix
 * attached internally. When using the flags with external servers,..
 * one should use the realName() method.
 */
public final class Flag {

    /*
     * IMPORTANT WARNING!!
     *
     * DO NOT ADD STATIC FIELDS TO THIS CLASS UNLESS THEY ARE
     * NEW FLAGS THAT GET PREDEFINED. IF YOU DO ADD A NEW PREDEFINED
     * FLAG ADD IT IN THE KNOWN_FLAGS FIELD BELOW TOO!
     */

    /*
     * IMAP Spec flags.
     */
    public static final Flag DELETED = new Flag("DELETED", "\\Deleted");
    public static final Flag SEEN = new Flag("SEEN", "\\Seen");
    public static final Flag ANSWERED = new Flag("ANSWERED", "\\Answered");
    public static final Flag FLAGGED = new Flag("FLAGGED", "\\Flagged");

    public static final Flag DRAFT = new Flag("DRAFT", "\\Draft");
    public static final Flag RECENT = new Flag("RECENT", "\\Recent");
    public static final Flag FORWARDED = new Flag("FORWARDED", "$forwarded");

    /*
     * The following flags are for internal library use only.
     */
    /**
     * Delete and remove from the LocalStore immediately.
     */
    public static final Flag X_DESTROYED = new Flag("X_DESTROYED");

    /**
     * Sending of an unsent message failed. It will be retried. Used to show status.
     */
    public static final Flag X_SEND_FAILED = new Flag("X_SEND_FAILED");

    /**
     * Sending of an unsent message is in progress.
     */
    public static final Flag X_SEND_IN_PROGRESS = new Flag("X_SEND_IN_PROGRESS");

    /**
     * Indicates that a message is fully downloaded from the server and can be viewed normally.
     * This does not include attachments, which are never downloaded fully.
     */
    public static final Flag X_DOWNLOADED_FULL = new Flag("X_DOWNLOADED_FULL");

    /**
     * Indicates that a message is partially downloaded from the server and can be viewed but
     * more content is available on the server.
     * This does not include attachments, which are never downloaded fully.
     */
    public static final Flag X_DOWNLOADED_PARTIAL = new Flag("X_DOWNLOADED_PARTIAL");

    /**
     * Indicates that the copy of a message to the Sent folder has started.
     */
    public static final Flag X_REMOTE_COPY_STARTED = new Flag("X_REMOTE_COPY_STARTED");

    /**
     * Messages with this flag have been migrated from database version 50 or earlier.
     * This earlier database format did not preserve the original mime structure of a
     * mail, which means messages migrated to the newer database structure may be
     * incomplete or broken.
     * TODO Messages with this flag should be redownloaded, if possible.
     */
    public static final Flag X_MIGRATED_FROM_V50 = new Flag("X_MIGRATED_FROM_V50");

    public static final Flag X_GOT_ALL_HEADERS = new Flag("X_GOT_ALL_HEADERS");

    /**
     * a list of all built-in flags that K-9 knows about.
     *
     * Protected for unit-test access.
     *
     * If you add a new flag field, make sure you update this list.
     */
    protected static final Set<Flag> KNOWN_FLAGS = new HashSet<Flag>(Arrays.asList(DELETED,
            SEEN,
            ANSWERED,
            FLAGGED,
            DRAFT,
            RECENT,
            FORWARDED,
            X_DESTROYED,
            X_SEND_FAILED,
            X_SEND_IN_PROGRESS,
            X_DOWNLOADED_FULL,
            X_DOWNLOADED_PARTIAL,
            X_REMOTE_COPY_STARTED,
            X_GOT_ALL_HEADERS));

    /**
     * flags that K-9 will always sync with the server
     */
    public static final Set<Flag> SYNC_FLAGS = new HashSet<Flag>(Arrays.asList(
            SEEN,
            FLAGGED,
            ANSWERED,
            FORWARDED));

    /**
     * Map from IMAP keywords to Flag object, in-memory, for easy access
     */
    public static final Map<String, Flag> REMEMBERED_KEYWORDS = new LinkedHashMap<String, Flag>();

    /*
     * Predefined Prefixes
     */
    private static final String USER_PREFIX = "USER_";

    // when internal name = name we refer to it as just name
    private final String mName;             // for use towards third party  ex. "\\Deleted"
    private final String mInternalName;     // for internal use in database,...   ex. "DELETED"
    protected final boolean mCustom;
    private String mTagName;                // for use in the UI, a user-defined keyword can be associated to a tag

    /**
     * When a Flag is created dynamically we know it's a custom flag.
     *
     * @param name Internal name of the flag.
     * @return Newly created Flag object.
     */
    public static Flag createFlag(String name) {
        Flag tmpFlag;
        if (!REMEMBERED_KEYWORDS.containsKey(name)) {
            tmpFlag = new Flag(USER_PREFIX + name, name, name);
            REMEMBERED_KEYWORDS.put(name, tmpFlag);
        } else {
            tmpFlag = REMEMBERED_KEYWORDS.get(name);
        }
        return tmpFlag;
    }

    private Flag(String name) {
        this(name, name);
    }

    /**
     * Create a new Flag. This doesn't create a custom flag, it's used to define
     * the predefined flags.
     *
     * @param internalName Name for internal use in database,...   ex. "DELETED"
     * @param name Name for use towards third party ( ex. "\\Deleted" )
     */
    private Flag(String internalName, String name) {
        this(internalName, name, false);
    }

    private Flag(String internalName, String name, boolean isCustom) {
        this.mName = name;
        this.mCustom = isCustom;
        this.mInternalName = internalName;
    }

    /**
     * Create a new Flag with an associated tag name. Tag names are user-facing strings that hide
     * the underlying IMAP keyword name.
     *
     * @param internalName Name for internal use in database,...   ex. "$label1"
     * @param name Name for use towards third party ( ex. "???????" )
     * @param tagName Name for use in the UI, ex. "Read Later"
     */
    private Flag(String internalName, String name, String tagName) {
        this(internalName, name, true);
        this.mTagName = tagName;
    }

    /**
     * Returns the predefined static flag object if any. Otherwise a new
     * custom flag is created and returned.
     *
     * When the name starts with the USER_PREFIX we don't add it again to
     * create a new one. This is the case for example when this is called with
     * strings retrieved from the database.
     *
     * IMPORTANT remember the name of the field of predefined flags must equal the
     * internal name!
     *
     * @param internalName Name of Flag wanted.
     * @return  Predefined Flag object if any otherwise new custom Flag.
     * @throws IllegalArgumentException Thrown when the field is not accessible.
     */
    public static Flag valueOf(String internalName) throws IllegalArgumentException {

        for (Flag f : KNOWN_FLAGS) {
            if (f.mName.equalsIgnoreCase(internalName)) {
                return f;
            }
        }

        if (internalName.startsWith(USER_PREFIX)) {
            return Flag.createFlag(internalName.substring(USER_PREFIX.length()));
        } else {
            return Flag.createFlag(internalName);
        }

    }


    /**
     * NOTE
     * This method is intended to be used with Flags which have a different
     * internal and external name! Else use valueOf(String) !!
     *
     *
     * Returns the predefined flag matching the given "real name". For
     * example "\\Deleted" will return the DELETED flag. When no such
     * flag exists we assume it's a custom flag and create it.
     *
     * ! this could be faster this way:
     * http://java.dzone.com/articles/enum-tricks-customized-valueof
     * Since it's only used once I don't see the point.
     *
     * @param name Real name to look for.
     * @return The flag that was found or created.
     */
    public static Flag valueOfByRealName(String name) {
        for (Flag f : KNOWN_FLAGS) {
            if (f.mName.equalsIgnoreCase(name)) {
                return f;
            }
        }

        return Flag.createFlag(name);
    }

    public void setTagName(String mTagName) {
        this.mTagName = mTagName;
    }

    @Override
    public String toString() {
        return mInternalName;
    }

    public String name() {
        return mInternalName;
    }

    public String tagName() {
        return mTagName;
    }

    /**
     * Returns the real keyword name without user prefix. This is
     * for non-internal use ( syncing with IMAP for example ).
     *
     * @return Real keyword string.
     */
    public String realName() {
        return mName;
    }

    public boolean isCustom() {
        return mCustom;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof Flag) {
            Flag f = (Flag)o;
            return ((f) == this ||
                    (f.mCustom == this.mCustom && f.mInternalName.equals(this.mInternalName)
                     && f.mName.equals(this.mName)));
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return mInternalName.hashCode();
    }
}

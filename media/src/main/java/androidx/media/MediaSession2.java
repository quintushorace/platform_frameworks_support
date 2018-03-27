/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IInterface;
import android.os.ResultReceiver;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.media.MediaPlayerBase.BuffState;
import androidx.media.MediaPlayerBase.PlayerEventCallback;
import androidx.media.MediaPlayerBase.PlayerState;
import androidx.media.MediaPlaylistAgent.PlaylistEventCallback;
import androidx.media.MediaPlaylistAgent.RepeatMode;
import androidx.media.MediaPlaylistAgent.ShuffleMode;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @hide
 * Allows a media app to expose its transport controls and playback information in a process to
 * other processes including the Android framework and other apps. Common use cases are as follows.
 * <ul>
 *     <li>Bluetooth/wired headset key events support</li>
 *     <li>Android Auto/Wearable support</li>
 *     <li>Separating UI process and playback process</li>
 * </ul>
 * <p>
 * A MediaSession2 should be created when an app wants to publish media playback information or
 * handle media keys. In general an app only needs one session for all playback, though multiple
 * sessions can be created to provide finer grain controls of media.
 * <p>
 * If you want to support background playback, {@link MediaSessionService2} is preferred
 * instead. With it, your playback can be revived even after playback is finished. See
 * {@link MediaSessionService2} for details.
 * <p>
 * A session can be obtained by {@link Builder}. The owner of the session may pass its session token
 * to other processes to allow them to create a {@link MediaController2} to interact with the
 * session.
 * <p>
 * When a session receive transport control commands, the session sends the commands directly to
 * the the underlying media player set by {@link Builder} or
 * {@link #updatePlayer}.
 * <p>
 * When an app is finished performing playback it must call {@link #close()} to clean up the session
 * and notify any controllers.
 * <p>
 * {@link MediaSession2} objects should be used on the thread on the looper.
 *
 * @see MediaSessionService2
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
@RestrictTo(LIBRARY_GROUP)
public class MediaSession2 implements AutoCloseable {
    /**
     * Command code for the custom command which can be defined by string action in the
     * {@link Command}.
     */
    public static final int COMMAND_CODE_CUSTOM = 0;

    /**
     * Command code for {@link MediaController2#play()}.
     * <p>
     * Command would be sent directly to the player if the session doesn't reject the request
     * through the {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYBACK_PLAY = 1;

    /**
     * Command code for {@link MediaController2#pause()}.
     * <p>
     * Command would be sent directly to the player if the session doesn't reject the request
     * through the {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYBACK_PAUSE = 2;

    /**
     * Command code for {@link MediaController2#stop()}.
     * <p>
     * Command would be sent directly to the player if the session doesn't reject the request
     * through the {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYBACK_STOP = 3;

    /**
     * Command code for {@link MediaController2#skipToNextItem()}.
     * <p>
     * Command would be sent directly to the player if the session doesn't reject the request
     * through the {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYBACK_SKIP_NEXT_ITEM = 4;

    /**
     * Command code for {@link MediaController2#skipToPreviousItem()}.
     * <p>
     * Command would be sent directly to the player if the session doesn't reject the request
     * through the {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYBACK_SKIP_PREV_ITEM = 5;

    /**
     * Command code for {@link MediaController2#prepare()}.
     * <p>
     * Command would be sent directly to the player if the session doesn't reject the request
     * through the {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYBACK_PREPARE = 6;

    /**
     * Command code for {@link MediaController2#fastForward()}.
     * <p>
     * This is transport control command. Command would be sent directly to the player if the
     * session doesn't reject the request through the
     * {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYBACK_FAST_FORWARD = 7;

    /**
     * Command code for {@link MediaController2#rewind()}.
     * <p>
     * Command would be sent directly to the player if the session doesn't reject the request
     * through the {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYBACK_REWIND = 8;

    /**
     * Command code for {@link MediaController2#seekTo(long)}.
     * <p>
     * Command would be sent directly to the player if the session doesn't reject the request
     * through the {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYBACK_SEEK_TO = 9;

    /**
     * Command code for both {@link MediaController2#setVolumeTo(int, int)}.
     * <p>
     * Command would set the device volume or send to the volume provider directly if the session
     * doesn't reject the request through the
     * {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYBACK_SET_VOLUME = 10;

    /**
     * Command code for both {@link MediaController2#adjustVolume(int, int)}.
     * <p>
     * Command would adjust the device volume or send to the volume provider directly if the session
     * doesn't reject the request through the
     * {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYBACK_ADJUST_VOLUME = 11;

    /**
     * Command code for {@link MediaController2#skipToPlaylistItem(MediaItem2)}.
     * <p>
     * Command would be sent directly to the playlist agent if the session doesn't reject the
     * request through the
     * {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYLIST_SKIP_TO_PLAYLIST_ITEM = 12;

    /**
     * Command code for {@link MediaController2#setShuffleMode(int)}.
     * <p>
     * Command would be sent directly to the playlist agent if the session doesn't reject the
     * request through the
     * {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYLIST_SET_SHUFFLE_MODE = 13;

    /**
     * Command code for {@link MediaController2#setRepeatMode(int)}.
     * <p>
     * Command would be sent directly to the playlist agent if the session doesn't reject the
     * request through the
     * {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYLIST_SET_REPEAT_MODE = 14;

    /**
     * Command code for {@link MediaController2#addPlaylistItem(int, MediaItem2)}.
     * <p>
     * Command would be sent directly to the playlist agent if the session doesn't reject the
     * request through the
     * {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYLIST_ADD_ITEM = 15;

    /**
     * Command code for {@link MediaController2#addPlaylistItem(int, MediaItem2)}.
     * <p>
     * Command would be sent directly to the playlist agent if the session doesn't reject the
     * request through the
     * {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYLIST_REMOVE_ITEM = 16;

    /**
     * Command code for {@link MediaController2#replacePlaylistItem(int, MediaItem2)}.
     * <p>
     * Command would be sent directly to the playlist agent if the session doesn't reject the
     * request through the
     * {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYLIST_REPLACE_ITEM = 17;

    /**
     * Command code for {@link MediaController2#getPlaylist()}. This will expose metadata
     * information to the controller.
     * <p>
     * Command would be sent directly to the playlist agent if the session doesn't reject the
     * request through the
     * {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYLIST_GET_LIST = 18;

    /**
     * Command code for {@link MediaController2#setPlaylist(List, MediaMetadata2)}.
     * <p>
     * Command would be sent directly to the playlist agent if the session doesn't reject the
     * request through the
     * {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYLIST_SET_LIST = 19;

    /**
     * Command code for {@link MediaController2#getPlaylistMetadata()}. This will expose
     * metadata information to the controller.
     * <p>
     * Command would be sent directly to the playlist agent if the session doesn't reject the
     * request through the
     * {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYLIST_GET_LIST_METADATA = 20;

    /**
     * Command code for {@link MediaController2#updatePlaylistMetadata(MediaMetadata2)}.
     * <p>
     * Command would be sent directly to the playlist agent if the session doesn't reject the
     * request through the
     * {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     */
    public static final int COMMAND_CODE_PLAYLIST_SET_LIST_METADATA = 21;

    /**
     * Command code for {@link MediaController2#playFromMediaId(String, Bundle)}.
     */
    public static final int COMMAND_CODE_PLAY_FROM_MEDIA_ID = 22;

    /**
     * Command code for {@link MediaController2#playFromUri(Uri, Bundle)}.
     */
    public static final int COMMAND_CODE_PLAY_FROM_URI = 23;

    /**
     * Command code for {@link MediaController2#playFromSearch(String, Bundle)}.
     */
    public static final int COMMAND_CODE_PLAY_FROM_SEARCH = 24;

    /**
     * Command code for {@link MediaController2#prepareFromMediaId(String, Bundle)}.
     */
    public static final int COMMAND_CODE_PREPARE_FROM_MEDIA_ID = 25;

    /**
     * Command code for {@link MediaController2#prepareFromUri(Uri, Bundle)}.
     */
    public static final int COMMAND_CODE_PREPARE_FROM_URI = 26;

    /**
     * Command code for {@link MediaController2#prepareFromSearch(String, Bundle)}.
     */
    public static final int COMMAND_CODE_PREPARE_FROM_SEARCH = 27;

    /**
     * Command code for {@link MediaController2#setRating(String, Rating2)}.
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    // TODO(jaewan): Unhide
    public static final int COMMAND_CODE_SET_RATING = 29;

    /**
     * Command code for {@link MediaBrowser2} specific functions that allows navigation and search
     * from the {@link MediaLibraryService2}. This would be ignored for a {@link MediaSession2},
     * not {@link MediaLibraryService2.MediaLibrarySession}.
     *
     * @see MediaBrowser2
     */
    public static final int COMMAND_CODE_BROWSER = 28;

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    @IntDef({ERROR_CODE_UNKNOWN_ERROR, ERROR_CODE_APP_ERROR, ERROR_CODE_NOT_SUPPORTED,
            ERROR_CODE_AUTHENTICATION_EXPIRED, ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED,
            ERROR_CODE_CONCURRENT_STREAM_LIMIT, ERROR_CODE_PARENTAL_CONTROL_RESTRICTED,
            ERROR_CODE_NOT_AVAILABLE_IN_REGION, ERROR_CODE_CONTENT_ALREADY_PLAYING,
            ERROR_CODE_SKIP_LIMIT_REACHED, ERROR_CODE_ACTION_ABORTED, ERROR_CODE_END_OF_QUEUE,
            ERROR_CODE_SETUP_REQUIRED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ErrorCode {}

    /**
     * This is the default error code and indicates that none of the other error codes applies.
     */
    public static final int ERROR_CODE_UNKNOWN_ERROR = 0;

    /**
     * Error code when the application state is invalid to fulfill the request.
     */
    public static final int ERROR_CODE_APP_ERROR = 1;

    /**
     * Error code when the request is not supported by the application.
     */
    public static final int ERROR_CODE_NOT_SUPPORTED = 2;

    /**
     * Error code when the request cannot be performed because authentication has expired.
     */
    public static final int ERROR_CODE_AUTHENTICATION_EXPIRED = 3;

    /**
     * Error code when a premium account is required for the request to succeed.
     */
    public static final int ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED = 4;

    /**
     * Error code when too many concurrent streams are detected.
     */
    public static final int ERROR_CODE_CONCURRENT_STREAM_LIMIT = 5;

    /**
     * Error code when the content is blocked due to parental controls.
     */
    public static final int ERROR_CODE_PARENTAL_CONTROL_RESTRICTED = 6;

    /**
     * Error code when the content is blocked due to being regionally unavailable.
     */
    public static final int ERROR_CODE_NOT_AVAILABLE_IN_REGION = 7;

    /**
     * Error code when the requested content is already playing.
     */
    public static final int ERROR_CODE_CONTENT_ALREADY_PLAYING = 8;

    /**
     * Error code when the application cannot skip any more songs because skip limit is reached.
     */
    public static final int ERROR_CODE_SKIP_LIMIT_REACHED = 9;

    /**
     * Error code when the action is interrupted due to some external event.
     */
    public static final int ERROR_CODE_ACTION_ABORTED = 10;

    /**
     * Error code when the playback navigation (previous, next) is not possible because the queue
     * was exhausted.
     */
    public static final int ERROR_CODE_END_OF_QUEUE = 11;

    /**
     * Error code when the session needs user's manual intervention.
     */
    public static final int ERROR_CODE_SETUP_REQUIRED = 12;

    /**
     * TODO: Fix {link DataSourceDesc}
     * Interface definition of a callback to be invoked when a {@link MediaItem2} in the playlist
     * didn't have a {link DataSourceDesc} but it's needed now for preparing or playing it.
     *
     * #see #setOnDataSourceMissingHelper
     */
    public interface OnDataSourceMissingHelper {
        /**
         * TODO: Fix {link DataSourceDesc}
         * Called when a {@link MediaItem2} in the playlist didn't have a {link DataSourceDesc}
         * but it's needed now for preparing or playing it. Returned data source descriptor will be
         * sent to the player directly to prepare or play the contents.
         * <p>
         * TODO: Fix {link DataSourceDesc}
         * An exception may be thrown if the returned {link DataSourceDesc} is duplicated in the
         * playlist, so items cannot be differentiated.
         *
         * @param session the session for this event
         * @param item media item from the controller
         * @return a data source descriptor if the media item. Can be {@code null} if the content
         *        isn't available.
         */
        @Nullable Object /*DataSourceDesc*/ onDataSourceMissing(@NonNull MediaSession2 session,
                @NonNull MediaItem2 item);
    }

    /**
     * Define a command that a {@link MediaController2} can send to a {@link MediaSession2}.
     * <p>
     * If {@link #getCommandCode()} isn't {@link #COMMAND_CODE_CUSTOM}), it's predefined command.
     * If {@link #getCommandCode()} is {@link #COMMAND_CODE_CUSTOM}), it's custom command and
     * {@link #getCustomCommand()} shouldn't be {@code null}.
     */
    public static final class Command {
        //private final CommandProvider mProvider;

        /**
         * TODO: javadoc
         */
        public Command(int commandCode) {
//            mProvider = ApiLoader.getProvider().createMediaSession2Command(
//                    this, commandCode, null, null);
        }

        /**
         * TODO: javadoc
         */
        public Command(@NonNull String action, @Nullable Bundle extras) {
            if (action == null) {
                throw new IllegalArgumentException("action shouldn't be null");
            }
//            mProvider = ApiLoader.getProvider().createMediaSession2Command(
//                    this, COMMAND_CODE_CUSTOM, action, extras);
        }

//        /**
//         * @hide
//         */
//        public CommandProvider getProvider() {
//            return mProvider;
//        }

        /**
         * TODO: javadoc
         */
        public int getCommandCode() {
            //return mProvider.getCommandCode_impl();
            return 0;
        }

        /**
         * TODO: javadoc
         */
        public @Nullable String getCustomCommand() {
            //return mProvider.getCustomCommand_impl();
            return null;
        }

        /**
         * TODO: javadoc
         */
        public @Nullable Bundle getExtras() {
            //return mProvider.getExtras_impl();
            return null;
        }

        /**
         * @return a new Bundle instance from the Command
         * @hide
         */
        @RestrictTo(LIBRARY_GROUP)
        public Bundle toBundle() {
            //return mProvider.toBundle_impl();
            return null;
        }

        @Override
        public boolean equals(Object obj) {
//            if (!(obj instanceof Command)) {
//                return false;
//            }
//            return mProvider.equals_impl(((Command) obj).mProvider);
            return false;
        }

        @Override
        public int hashCode() {
            //return mProvider.hashCode_impl();
            return 0;
        }

        /**
         * @return a new Command instance from the Bundle
         * @hide
         */
        @RestrictTo(LIBRARY_GROUP)
        public static Command fromBundle(@NonNull Bundle command) {
            //return ApiLoader.getProvider().fromBundle_MediaSession2Command(context, command);
            return null;
        }
    }

    /**
     * Represent set of {@link Command}.
     */
    public static final class CommandGroup {
        //private final CommandGroupProvider mProvider;

        /**
         * TODO: javadoc
         */
        public CommandGroup() {
//            mProvider = ApiLoader.getProvider().createMediaSession2CommandGroup(
//                    context, this, null);
        }

        /**
         * TODO: javadoc
         */
        public CommandGroup(@Nullable CommandGroup others) {
//            mProvider = ApiLoader.getProvider().createMediaSession2CommandGroup(
//                    context, this, others);
        }

//        /**
//         * @hide
//         */
//        public CommandGroup(@NonNull CommandGroupProvider provider) {
//            mProvider = provider;
//        }

        /**
         * TODO: javadoc
         */
        public void addCommand(@NonNull Command command) {
            //mProvider.addCommand_impl(command);
        }

        /**
         * TODO: javadoc
         */
        public void addAllPredefinedCommands() {
            //mProvider.addAllPredefinedCommands_impl();
        }

        /**
         * TODO: javadoc
         */
        public void removeCommand(@NonNull Command command) {
            //mProvider.removeCommand_impl(command);
        }

        /**
         * TODO: javadoc
         */
        public boolean hasCommand(@NonNull Command command) {
            //return mProvider.hasCommand_impl(command);
            return false;
        }

        /**
         * TODO: javadoc
         */
        public boolean hasCommand(int code) {
            //return mProvider.hasCommand_impl(code);
            return false;
        }

        /**
         * TODO: javadoc
         */
        public /*@NonNull*/ List<Command> getCommands() {
            //return mProvider.getCommands_impl();
            return null;
        }

//        /**
//         * @hide
//         */
//        public @NonNull CommandGroupProvider getProvider() {
//            return mProvider;
//        }

        /**
         * @return new bundle from the CommandGroup
         * @hide
         */
        @RestrictTo(LIBRARY_GROUP)
        public /*@NonNull*/ Bundle toBundle() {
            //return mProvider.toBundle_impl();
            return null;
        }

        /**
         * @return new instance of CommandGroup from the bundle
         * @hide
         */
        @RestrictTo(LIBRARY_GROUP)
        public static @Nullable CommandGroup fromBundle(Context context, Bundle commands) {
            //return ApiLoader.getProvider()
            //        .fromBundle_MediaSession2CommandGroup(context, commands);
            return null;
        }
    }

    /**
     * Callback to be called for all incoming commands from {@link MediaController2}s.
     * <p>
     * If it's not set, the session will accept all controllers and all incoming commands by
     * default.
     */
    // TODO(jaewan): Move this to updatable for default implementation (b/74091963)
    public abstract static class SessionCallback {
        /**
         * Called when a controller is created for this session. Return allowed commands for
         * controller. By default it allows all connection requests and commands.
         * <p>
         * You can reject the connection by return {@code null}. In that case, controller receives
         * {@link MediaController2.ControllerCallback#onDisconnected(MediaController2)} and cannot
         * be usable.
         *
         * @param session the session for this event
         * @param controller controller information.
         * @return allowed commands. Can be {@code null} to reject connection.
         */
        public @Nullable CommandGroup onConnect(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller) {
            CommandGroup commands = new CommandGroup();
            commands.addAllPredefinedCommands();
            return commands;
        }

        /**
         * Called when a controller is disconnected
         *
         * @param session the session for this event
         * @param controller controller information
         */
        public void onDisconnected(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller) { }

        /**
         * Called when a controller sent a command that will be sent directly to the player. Return
         * {@code false} here to reject the request and stop sending command to the player.
         *
         * @param session the session for this event
         * @param controller controller information.
         * @param command a command. This method will be called for every single command.
         * @return {@code true} if you want to accept incoming command. {@code false} otherwise.
         * @see #COMMAND_CODE_PLAYBACK_PLAY
         * @see #COMMAND_CODE_PLAYBACK_PAUSE
         * @see #COMMAND_CODE_PLAYBACK_STOP
         * @see #COMMAND_CODE_PLAYBACK_SKIP_NEXT_ITEM
         * @see #COMMAND_CODE_PLAYBACK_SKIP_PREV_ITEM
         * @see #COMMAND_CODE_PLAYBACK_PREPARE
         * @see #COMMAND_CODE_PLAYBACK_FAST_FORWARD
         * @see #COMMAND_CODE_PLAYBACK_REWIND
         * @see #COMMAND_CODE_PLAYBACK_SEEK_TO
         * @see #COMMAND_CODE_PLAYLIST_SKIP_TO_PLAYLIST_ITEM
         * @see #COMMAND_CODE_PLAYLIST_ADD_ITEM
         * @see #COMMAND_CODE_PLAYLIST_REMOVE_ITEM
         * @see #COMMAND_CODE_PLAYLIST_GET_LIST
         * @see #COMMAND_CODE_PLAYBACK_SET_VOLUME
         */
        public boolean onCommandRequest(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller, @NonNull Command command) {
            return true;
        }

        /**
         * Called when a controller set rating of a media item through
         * {@link MediaController2#setRating(String, Rating2)}.
         * <p>
         * To allow setting user rating for a {@link MediaItem2}, the media item's metadata
         * should have {@link Rating2} with the key {@link MediaMetadata2#METADATA_KEY_USER_RATING},
         * in order to provide possible rating style for controller. Controller will follow the
         * rating style.
         *
         * @param session the session for this event
         * @param controller controller information
         * @param mediaId media id from the controller
         * @param rating new rating from the controller
         */
        public void onSetRating(@NonNull MediaSession2 session, @NonNull ControllerInfo controller,
                @NonNull String mediaId, @NonNull Rating2 rating) { }

        /**
         * Called when a controller sent a custom command through
         * {@link MediaController2#sendCustomCommand(Command, Bundle, ResultReceiver)}.
         *
         * @param session the session for this event
         * @param controller controller information
         * @param customCommand custom command.
         * @param args optional arguments
         * @param cb optional result receiver
         */
        public void onCustomCommand(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller, @NonNull Command customCommand,
                @Nullable Bundle args, @Nullable ResultReceiver cb) { }

        /**
         * Called when a controller requested to play a specific mediaId through
         * {@link MediaController2#playFromMediaId(String, Bundle)}.
         *
         * @param session the session for this event
         * @param controller controller information
         * @param mediaId media id
         * @param extras optional extra bundle
         * @see #COMMAND_CODE_PLAY_FROM_MEDIA_ID
         */
        public void onPlayFromMediaId(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller, @NonNull String mediaId,
                @Nullable Bundle extras) { }

        /**
         * Called when a controller requested to begin playback from a search query through
         * {@link MediaController2#playFromSearch(String, Bundle)}
         * <p>
         * An empty query indicates that the app may play any music. The implementation should
         * attempt to make a smart choice about what to play.
         *
         * @param session the session for this event
         * @param controller controller information
         * @param query query string. Can be empty to indicate any suggested media
         * @param extras optional extra bundle
         * @see #COMMAND_CODE_PLAY_FROM_SEARCH
         */
        public void onPlayFromSearch(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller, @NonNull String query,
                @Nullable Bundle extras) { }

        /**
         * Called when a controller requested to play a specific media item represented by a URI
         * through {@link MediaController2#playFromUri(Uri, Bundle)}
         *
         * @param session the session for this event
         * @param controller controller information
         * @param uri uri
         * @param extras optional extra bundle
         * @see #COMMAND_CODE_PLAY_FROM_URI
         */
        public void onPlayFromUri(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller, @NonNull Uri uri,
                @Nullable Bundle extras) { }

        /**
         * Called when a controller requested to prepare for playing a specific mediaId through
         * {@link MediaController2#prepareFromMediaId(String, Bundle)}.
         * <p>
         * During the preparation, a session should not hold audio focus in order to allow other
         * sessions play seamlessly. The state of playback should be updated to
         * {@link MediaPlayerBase#PLAYER_STATE_PAUSED} after the preparation is done.
         * <p>
         * The playback of the prepared content should start in the later calls of
         * {@link MediaSession2#play()}.
         * <p>
         * Override {@link #onPlayFromMediaId} to handle requests for starting
         * playback without preparation.
         *
         * @param session the session for this event
         * @param controller controller information
         * @param mediaId media id to prepare
         * @param extras optional extra bundle
         * @see #COMMAND_CODE_PREPARE_FROM_MEDIA_ID
         */
        public void onPrepareFromMediaId(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller, @NonNull String mediaId,
                @Nullable Bundle extras) { }

        /**
         * Called when a controller requested to prepare playback from a search query through
         * {@link MediaController2#prepareFromSearch(String, Bundle)}.
         * <p>
         * An empty query indicates that the app may prepare any music. The implementation should
         * attempt to make a smart choice about what to play.
         * <p>
         * The state of playback should be updated to {@link MediaPlayerBase#PLAYER_STATE_PAUSED}
         * after the preparation is done. The playback of the prepared content should start in the
         * later calls of {@link MediaSession2#play()}.
         * <p>
         * Override {@link #onPlayFromSearch} to handle requests for starting playback without
         * preparation.
         *
         * @param session the session for this event
         * @param controller controller information
         * @param query query string. Can be empty to indicate any suggested media
         * @param extras optional extra bundle
         * @see #COMMAND_CODE_PREPARE_FROM_SEARCH
         */
        public void onPrepareFromSearch(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller, @NonNull String query,
                @Nullable Bundle extras) { }

        /**
         * Called when a controller requested to prepare a specific media item represented by a URI
         * through {@link MediaController2#prepareFromUri(Uri, Bundle)}.
         * <p>
         * During the preparation, a session should not hold audio focus in order to allow
         * other sessions play seamlessly. The state of playback should be updated to
         * {@link MediaPlayerBase#PLAYER_STATE_PAUSED} after the preparation is done.
         * <p>
         * The playback of the prepared content should start in the later calls of
         * {@link MediaSession2#play()}.
         * <p>
         * Override {@link #onPlayFromUri} to handle requests for starting playback without
         * preparation.
         *
         * @param session the session for this event
         * @param controller controller information
         * @param uri uri
         * @param extras optional extra bundle
         * @see #COMMAND_CODE_PREPARE_FROM_URI
         */
        public void onPrepareFromUri(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller, @NonNull Uri uri, @Nullable Bundle extras) { }

        /**
         * Called when the player's current playing item is changed
         * <p>
         * When it's called, you should invalidate previous playback information and wait for later
         * callbacks.
         *
         * @param session the controller for this event
         * @param player the player for this event
         * @param item new item
         */
        // TODO(jaewan): Use this (b/74316764)
        public void onCurrentMediaItemChanged(@NonNull MediaSession2 session,
                @NonNull MediaPlayerBase player, @NonNull MediaItem2 item) { }

        /**
         * Called when the player is <i>prepared</i>, i.e. it is ready to play the content
         * referenced by the given data source.
         * @param session the session for this event
         * @param player the player for this event
         * @param item the media item for which buffering is happening
         */
        public void onMediaPrepared(@NonNull MediaSession2 session, @NonNull MediaPlayerBase player,
                @NonNull MediaItem2 item) { }

        /**
         * Called to indicate that the state of the player has changed.
         * See {@link MediaPlayerBase#getPlayerState()} for polling the player state.
         * @param session the session for this event
         * @param player the player for this event
         * @param state the new state of the player.
         */
        public void onPlayerStateChanged(@NonNull MediaSession2 session,
                @NonNull MediaPlayerBase player, @PlayerState int state) { }

        /**
         * Called to report buffering events for a data source.
         *
         * @param session the session for this event
         * @param player the player for this event
         * @param item the media item for which buffering is happening.
         * @param state the new buffering state.
         */
        public void onBufferingStateChanged(@NonNull MediaSession2 session,
                @NonNull MediaPlayerBase player, @NonNull MediaItem2 item, @BuffState int state) { }

        /**
         * Called when a playlist is changed from the {@link MediaPlaylistAgent}.
         * <p>
         * This is called when the underlying agent has called
         * {@link PlaylistEventCallback#onPlaylistChanged(MediaPlaylistAgent,
         * List, MediaMetadata2)}.
         *
         * @param session the session for this event
         * @param playlistAgent playlist agent for this event
         * @param list new playlist
         * @param metadata new metadata
         */
        public void onPlaylistChanged(@NonNull MediaSession2 session,
                @NonNull MediaPlaylistAgent playlistAgent, @NonNull List<MediaItem2> list,
                @Nullable MediaMetadata2 metadata) { }

        /**
         * Called when a playlist metadata is changed.
         *
         * @param session the session for this event
         * @param playlistAgent playlist agent for this event
         * @param metadata new metadata
         */
        public void onPlaylistMetadataChanged(@NonNull MediaSession2 session,
                @NonNull MediaPlaylistAgent playlistAgent, @Nullable MediaMetadata2 metadata) { }

        /**
         * Called when the shuffle mode is changed.
         *
         * @param session the session for this event
         * @param playlistAgent playlist agent for this event
         * @param shuffleMode repeat mode
         * @see MediaPlaylistAgent#SHUFFLE_MODE_NONE
         * @see MediaPlaylistAgent#SHUFFLE_MODE_ALL
         * @see MediaPlaylistAgent#SHUFFLE_MODE_GROUP
         */
        public void onShuffleModeChanged(@NonNull MediaSession2 session,
                @NonNull MediaPlaylistAgent playlistAgent,
                @MediaPlaylistAgent.ShuffleMode int shuffleMode) { }

        /**
         * Called when the repeat mode is changed.
         *
         * @param session the session for this event
         * @param playlistAgent playlist agent for this event
         * @param repeatMode repeat mode
         * @see MediaPlaylistAgent#REPEAT_MODE_NONE
         * @see MediaPlaylistAgent#REPEAT_MODE_ONE
         * @see MediaPlaylistAgent#REPEAT_MODE_ALL
         * @see MediaPlaylistAgent#REPEAT_MODE_GROUP
         */
        public void onRepeatModeChanged(@NonNull MediaSession2 session,
                @NonNull MediaPlaylistAgent playlistAgent,
                @MediaPlaylistAgent.RepeatMode int repeatMode) { }
    }

    /**
     * Base builder class for MediaSession2 and its subclass. Any change in this class should be
     * also applied to the subclasses {@link MediaSession2.Builder} and
     * {@link MediaLibraryService2.MediaLibrarySession.Builder}.
     * <p>
     * APIs here should be package private, but should have documentations for developers.
     * Otherwise, javadoc will generate documentation with the generic types such as follows.
     * <pre>U extends BuilderBase<T, U, C> setSessionCallback(Executor executor, C callback)</pre>
     * <p>
     * This class is hidden to prevent from generating test stub, which fails with
     * 'unexpected bound' because it tries to auto generate stub class as follows.
     * <pre>abstract static class BuilderBase<
     *      T extends android.media.MediaSession2,
     *      U extends android.media.MediaSession2.BuilderBase<
     *              T, U, C extends android.media.MediaSession2.SessionCallback>, C></pre>
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    abstract static class BuilderBase
            <T extends MediaSession2, U extends BuilderBase<T, U, C>, C extends SessionCallback> {
        final Context mContext;
        MediaPlayerBase mPlayer;
        String mId;
        Executor mCallbackExecutor;
        C mCallback;
        MediaPlaylistAgent mPlaylistAgent;
        VolumeProvider2 mVolumeProvider;
        PendingIntent mSessionActivity;

        BuilderBase(Context context) {
            if (context == null) {
                throw new IllegalArgumentException("context shouldn't be null");
            }
            mContext = context;
            // Ensure non-null
            mId = "";
        }

        /**
         * Sets the underlying {@link MediaPlayerBase} for this session to dispatch incoming event
         * to.
         *
         * @param player a {@link MediaPlayerBase} that handles actual media playback in your app.
         */
        @NonNull U setPlayer(@NonNull MediaPlayerBase player) {
            if (player == null) {
                throw new IllegalArgumentException("player shouldn't be null");
            }
            mPlayer = player;
            return (U) this;
        }

        /**
         * Sets the {@link MediaPlaylistAgent} for this session to manages playlist of the
         * underlying {@link MediaPlayerBase}. The playlist agent should manage
         * {@link MediaPlayerBase} for calling {@link MediaPlayerBase#setNextDataSources(List)}.
         * <p>
         * If the {@link MediaPlaylistAgent} isn't set, session will create the default playlist
         * agent.
         *
         * @param playlistAgent a {@link MediaPlaylistAgent} that manages playlist of the
         *                      {@code player}
         */
        U setPlaylistAgent(@NonNull MediaPlaylistAgent playlistAgent) {
            if (playlistAgent == null) {
                throw new IllegalArgumentException("playlistAgent shouldn't be null");
            }
            mPlaylistAgent = playlistAgent;
            return (U) this;
        }

        /**
         * Sets the {@link VolumeProvider2} for this session to handle volume events. If not set,
         * system will adjust the appropriate stream volume for this session's player.
         *
         * @param volumeProvider The provider that will receive volume button events.
         */
        @NonNull U setVolumeProvider(@Nullable VolumeProvider2 volumeProvider) {
            mVolumeProvider = volumeProvider;
            return (U) this;
        }

        /**
         * Set an intent for launching UI for this Session. This can be used as a
         * quick link to an ongoing media screen. The intent should be for an
         * activity that may be started using {@link Context#startActivity(Intent)}.
         *
         * @param pi The intent to launch to show UI for this session.
         */
        @NonNull U setSessionActivity(@Nullable PendingIntent pi) {
            mSessionActivity = pi;
            return (U) this;
        }

        /**
         * Set ID of the session. If it's not set, an empty string with used to create a session.
         * <p>
         * Use this if and only if your app supports multiple playback at the same time and also
         * wants to provide external apps to have finer controls of them.
         *
         * @param id id of the session. Must be unique per package.
         * @throws IllegalArgumentException if id is {@code null}
         * @return
         */
        @NonNull U setId(@NonNull String id) {
            if (id == null) {
                throw new IllegalArgumentException("id shouldn't be null");
            }
            mId = id;
            return (U) this;
        }

        /**
         * Set callback for the session.
         *
         * @param executor callback executor
         * @param callback session callback.
         * @return
         */
        @NonNull U setSessionCallback(@NonNull Executor executor, @NonNull C callback) {
            if (executor == null) {
                throw new IllegalArgumentException("executor shouldn't be null");
            }
            if (callback == null) {
                throw new IllegalArgumentException("callback shouldn't be null");
            }
            mCallbackExecutor = executor;
            mCallback = callback;
            return (U) this;
        }

        /**
         * Build {@link MediaSession2}.
         *
         * @return a new session
         * @throws IllegalStateException if the session with the same id is already exists for the
         *      package.
         */
        abstract @NonNull T build();
    }

    /**
     * Builder for {@link MediaSession2}.
     * <p>
     * Any incoming event from the {@link MediaController2} will be handled on the thread
     * that created session with the {@link Builder#build()}.
     */
    public static final class Builder extends BuilderBase<MediaSession2, Builder, SessionCallback> {
        public Builder(Context context) {
            super(context);
        }


        @Override
        public @NonNull Builder setPlayer(@NonNull MediaPlayerBase player) {
            return super.setPlayer(player);
        }

        @Override
        public @NonNull Builder setPlaylistAgent(@NonNull MediaPlaylistAgent playlistAgent) {
            return super.setPlaylistAgent(playlistAgent);
        }

        @Override
        public @NonNull Builder setVolumeProvider(@Nullable VolumeProvider2 volumeProvider) {
            return super.setVolumeProvider(volumeProvider);
        }

        @Override
        public @NonNull Builder setSessionActivity(@Nullable PendingIntent pi) {
            return super.setSessionActivity(pi);
        }

        @Override
        public @NonNull Builder setId(@NonNull String id) {
            return super.setId(id);
        }

        @Override
        public @NonNull Builder setSessionCallback(@NonNull Executor executor,
                @NonNull SessionCallback callback) {
            return super.setSessionCallback(executor, callback);
        }

        @Override
        public @NonNull MediaSession2 build() {
            return new MediaSession2(mContext,
                    new MediaSessionCompat(mContext, mId), mId, mPlayer, mPlaylistAgent,
                    mVolumeProvider, mSessionActivity, mCallbackExecutor, mCallback);
        }
    }

    /**
     * Information of a controller.
     */
    public static final class ControllerInfo {
        //private final ControllerInfoProvider mProvider;

        /**
         * @hide
         */
        @RestrictTo(LIBRARY_GROUP)
        public ControllerInfo(@NonNull Context context, int uid, int pid,
                @NonNull String packageName, @NonNull IInterface callback) {
//            mProvider = ApiLoader.getProvider().createMediaSession2ControllerInfo(
//                    context, this, uid, pid, packageName, callback);
        }

        /**
         * @return package name of the controller
         */
        public /*@NonNull*/ String getPackageName() {
            //return mProvider.getPackageName_impl();
            return null;
        }

        /**
         * @return uid of the controller
         */
        public int getUid() {
            //return mProvider.getUid_impl();
            return 0;
        }

        /**
         * Return if the controller has granted {@code android.permission.MEDIA_CONTENT_CONTROL} or
         * has a enabled notification listener so can be trusted to accept connection and incoming
         * command request.
         *
         * @return {@code true} if the controller is trusted.
         */
        public boolean isTrusted() {
            //return mProvider.isTrusted_impl();
            return false;
        }

//        /**
//         * @hide
//         */
//        public @NonNull ControllerInfoProvider getProvider() {
//            return mProvider;
//        }

        @Override
        public int hashCode() {
            //return mProvider.hashCode_impl();
            return 0;
        }

        @Override
        public boolean equals(Object obj) {
            //return mProvider.equals_impl(obj);
            return false;
        }

        @Override
        public String toString() {
            //return mProvider.toString_impl();
            return null;
        }
    }

    /**
     * Button for a {@link Command} that will be shown by the controller.
     * <p>
     * It's up to the controller's decision to respect or ignore this customization request.
     */
    public static final class CommandButton {
        //private final CommandButtonProvider mProvider;

//        /**
//         * @hide
//         */
//        public CommandButton(CommandButtonProvider provider) {
//            mProvider = provider;
//        }

        /**
         * Get command associated with this button. Can be {@code null} if the button isn't enabled
         * and only providing placeholder.
         *
         * @return command or {@code null}
         */
        public @Nullable Command getCommand() {
            //return mProvider.getCommand_impl();
            return null;
        }

        /**
         * Resource id of the button in this package. Can be {@code 0} if the command is predefined
         * and custom icon isn't needed.
         *
         * @return resource id of the icon. Can be {@code 0}.
         */
        public int getIconResId() {
            //return mProvider.getIconResId_impl();
            return 0;
        }

        /**
         * Display name of the button. Can be {@code null} or empty if the command is predefined
         * and custom name isn't needed.
         *
         * @return custom display name. Can be {@code null} or empty.
         */
        public @Nullable String getDisplayName() {
            //return mProvider.getDisplayName_impl();
            return null;
        }

        /**
         * Extra information of the button. It's private information between session and controller.
         *
         * @return
         */
        public @Nullable Bundle getExtras() {
            //return mProvider.getExtras_impl();
            return null;
        }

        /**
         * Return whether it's enabled
         *
         * @return {@code true} if enabled. {@code false} otherwise.
         */
        public boolean isEnabled() {
            //return mProvider.isEnabled_impl();
            return false;
        }

//        /**
//         * @hide
//         */
//        public @NonNull CommandButtonProvider getProvider() {
//            return mProvider;
//        }

        /**
         * Builder for {@link CommandButton}.
         */
        public static final class Builder {
            //private final CommandButtonProvider.BuilderProvider mProvider;

            /**
             * TODO: javadoc
             */
            public Builder(@NonNull Context context) {
//                mProvider = ApiLoader.getProvider().createMediaSession2CommandButtonBuilder(
//                        context, this);
            }

            /**
             * TODO: javadoc
             */
            public @NonNull Builder setCommand(@Nullable Command command) {
                //return mProvider.setCommand_impl(command);
                return this;
            }

            /**
             * TODO: javadoc
             */
            public @NonNull Builder setIconResId(int resId) {
                //return mProvider.setIconResId_impl(resId);
                return this;
            }

            /**
             * TODO: javadoc
             */
            public @NonNull Builder setDisplayName(@Nullable String displayName) {
                //return mProvider.setDisplayName_impl(displayName);
                return this;
            }

            /**
             * TODO: javadoc
             */
            public @NonNull Builder setEnabled(boolean enabled) {
                //return mProvider.setEnabled_impl(enabled);
                return this;
            }

            /**
             * TODO: javadoc
             */
            public @NonNull Builder setExtras(@Nullable Bundle extras) {
                //return mProvider.setExtras_impl(extras);
                return this;
            }

            /**
             * TODO: javadoc
             */
            public /*@NonNull*/ CommandButton build() {
                //return mProvider.build_impl();
                return null;
            }
        }
    }

    private final MediaSessionCompat mSessionCompat;
    private final String mId;
    private final Executor mCallbackExecutor;
    private final SessionCallback mCallback;
    private final SessionToken2 mSessionToken;
    private final AudioManager mAudioManager;
    private final PendingIntent mSessionActivity;
    private final PlayerEventCallback mPlayerEventCallback;
    private final PlaylistEventCallback mPlaylistEventCallback;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private MediaPlayerBase mPlayer;
    @GuardedBy("mLock")
    private MediaPlaylistAgent mPlaylistAgent;
    //@GuardedBy("mLock")
    //private SessionPlaylistAgent mSessionPlaylistAgent;
    @GuardedBy("mLock")
    private VolumeProvider2 mVolumeProvider;
    //@GuardedBy("mLock")
    //private PlaybackInfo mPlaybackInfo;
    @GuardedBy("mLock")
    private OnDataSourceMissingHelper mDsmHelper;

    MediaSession2(Context context, MediaSessionCompat sessionCompat, String id,
            MediaPlayerBase player, MediaPlaylistAgent playlistAgent,
            VolumeProvider2 volumeProvider, PendingIntent sessionActivity,
            Executor callbackExecutor, SessionCallback callback) {
        mSessionCompat = sessionCompat;
        mId = id;
        mCallback = callback;
        mCallbackExecutor = callbackExecutor;
        mSessionActivity = sessionActivity;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        // TODO: Set values properly
        mSessionToken = null;
        updatePlayer(player, playlistAgent, volumeProvider);
        mPlayerEventCallback = null;
        mPlaylistEventCallback = null;
    }

    /**
     * Sets the underlying {@link MediaPlayerBase} and {@link MediaPlaylistAgent} for this session
     * to dispatch incoming event to.
     * <p>
     * When a {@link MediaPlaylistAgent} is specified here, the playlist agent should manage
     * {@link MediaPlayerBase} for calling {@link MediaPlayerBase#setNextDataSources(List)}.
     * <p>
     * If the {@link MediaPlaylistAgent} isn't set, session will recreate the default playlist
     * agent.
     *
     * @param player a {@link MediaPlayerBase} that handles actual media playback in your app
     * @param playlistAgent a {@link MediaPlaylistAgent} that manages playlist of the {@code player}
     * @param volumeProvider a {@link VolumeProvider2}. If {@code null}, system will adjust the
     *                       appropriate stream volume for this session's player.
     */
    public void updatePlayer(@NonNull MediaPlayerBase player,
            @Nullable MediaPlaylistAgent playlistAgent, @Nullable VolumeProvider2 volumeProvider) {
        //mProvider.updatePlayer_impl(player, playlistAgent, volumeProvider);
    }

    @Override
    public void close() {
        //mProvider.close_impl();
    }

    /**
     * @return player
     */
    public /*@NonNull*/ MediaPlayerBase getPlayer() {
        //return mProvider.getPlayer_impl();
        return null;
    }

    /**
     * @return playlist agent
     */
    public /*@NonNull*/ MediaPlaylistAgent getPlaylistAgent() {
        //return mProvider.getPlaylistAgent_impl();
        return null;
    }

    /**
     * @return volume provider
     */
    public @Nullable VolumeProvider2 getVolumeProvider() {
        //return mProvider.getVolumeProvider_impl();
        return null;
    }

    /**
     * Returns the {@link SessionToken2} for creating {@link MediaController2}.
     */
    public /*@NonNull*/ SessionToken2 getToken() {
        //return mProvider.getToken_impl();
        return null;
    }

    /**
     * TODO: add javadoc
     */
    public /*@NonNull*/ List<ControllerInfo> getConnectedControllers() {
        //return mProvider.getConnectedControllers_impl();
        return null;
    }

    /**
     * Set the {@link AudioFocusRequest} to obtain the audio focus
     *
     * @param afr the full request parameters
     */
    public void setAudioFocusRequest(@Nullable AudioFocusRequest afr) {
        // TODO(jaewan): implement this (b/72529899)
        // mProvider.setAudioFocusRequest_impl(focusGain);
    }

    /**
     * Sets ordered list of {@link CommandButton} for controllers to build UI with it.
     * <p>
     * It's up to controller's decision how to represent the layout in its own UI.
     * Here's the same way
     * (layout[i] means a CommandButton at index i in the given list)
     * For 5 icons row
     *      layout[3] layout[1] layout[0] layout[2] layout[4]
     * For 3 icons row
     *      layout[1] layout[0] layout[2]
     * For 5 icons row with overflow icon (can show +5 extra buttons with overflow button)
     *      expanded row:   layout[5] layout[6] layout[7] layout[8] layout[9]
     *      main row:       layout[3] layout[1] layout[0] layout[2] layout[4]
     * <p>
     * This API can be called in the
     * {@link SessionCallback#onConnect(MediaSession2, ControllerInfo)}.
     *
     * @param controller controller to specify layout.
     * @param layout ordered list of layout.
     */
    public void setCustomLayout(@NonNull ControllerInfo controller,
            @NonNull List<CommandButton> layout) {
        //mProvider.setCustomLayout_impl(controller, layout);
    }

    /**
     * Set the new allowed command group for the controller
     *
     * @param controller controller to change allowed commands
     * @param commands new allowed commands
     */
    public void setAllowedCommands(@NonNull ControllerInfo controller,
            @NonNull CommandGroup commands) {
        //mProvider.setAllowedCommands_impl(controller, commands);
    }

    /**
     * Send custom command to all connected controllers.
     *
     * @param command a command
     * @param args optional argument
     */
    public void sendCustomCommand(@NonNull Command command, @Nullable Bundle args) {
        //mProvider.sendCustomCommand_impl(command, args);
    }

    /**
     * Send custom command to a specific controller.
     *
     * @param command a command
     * @param args optional argument
     * @param receiver result receiver for the session
     */
    public void sendCustomCommand(@NonNull ControllerInfo controller, @NonNull Command command,
            @Nullable Bundle args, @Nullable ResultReceiver receiver) {
        // Equivalent to the MediaController.sendCustomCommand(Action action, ResultReceiver r);
        //mProvider.sendCustomCommand_impl(controller, command, args, receiver);
    }

    /**
     * Play playback
     * <p>
     * This calls {@link MediaPlayerBase#play()}.
     */
    public void play() {
        //mProvider.play_impl();
    }

    /**
     * Pause playback.
     * <p>
     * This calls {@link MediaPlayerBase#pause()}.
     */
    public void pause() {
        //mProvider.pause_impl();
    }

    /**
     * Stop playback, and reset the player to the initial state.
     * <p>
     * This calls {@link MediaPlayerBase#reset()}.
     */
    public void stop() {
        //mProvider.stop_impl();
    }

    /**
     * Request that the player prepare its playback. In other words, other sessions can continue
     * to play during the preparation of this session. This method can be used to speed up the
     * start of the playback. Once the preparation is done, the session will change its playback
     * state to {@link MediaPlayerBase#PLAYER_STATE_PAUSED}. Afterwards, {@link #play} can be called
     * to start playback.
     * <p>
     * This calls {@link MediaPlayerBase#reset()}.
     */
    public void prepare() {
        //mProvider.prepare_impl();
    }

    /**
     * Fast forwards playback. If playback is already fast forwarding this may increase the rate.
     */
    public void fastForward() {
        //mProvider.fastForward_impl();
    }

    /**
     * Rewinds playback. If playback is already rewinding this may increase the rate.
     */
    public void rewind() {
        //mProvider.rewind_impl();
    }

    /**
     * Move to a new location in the media stream.
     *
     * @param pos Position to move to, in milliseconds.
     */
    public void seekTo(long pos) {
        //mProvider.seekTo_impl(pos);
    }

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public void skipForward() {
        // To match with KEYCODE_MEDIA_SKIP_FORWARD
    }

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public void skipBackward() {
        // To match with KEYCODE_MEDIA_SKIP_BACKWARD
    }

    /**
     * Notify errors to the connected controllers
     *
     * @param errorCode error code
     * @param extras extras
     */
    public void notifyError(@ErrorCode int errorCode, @Nullable Bundle extras) {
        //mProvider.notifyError_impl(errorCode, extras);
    }

    /**
     * Gets the current player state.
     *
     * @return the current player state
     * @hide
     */
    // TODO(jaewan): Unhide (b/74578458)
    @RestrictTo(LIBRARY_GROUP)
    public @PlayerState int getPlayerState() {
        //return mProvider.getPlayerState_impl();
        return 0;
    }

    /**
     * Gets the current position.
     *
     * @return the current playback position in ms, or {@link MediaPlayerBase#UNKNOWN_TIME} if
     *         unknown.
     * @hide
     */
    // TODO(jaewan): Unhide (b/74578458)
    @RestrictTo(LIBRARY_GROUP)
    public long getPosition() {
        //return mProvider.getPosition_impl();
        return 0L;
    }

    /**
     * Gets the buffered position, or {@link MediaPlayerBase#UNKNOWN_TIME} if unknown.
     *
     * @return the buffered position in ms, or {@link MediaPlayerBase#UNKNOWN_TIME}.
     * @hide
     */
    // TODO(jaewan): Unhide (b/74578458)
    @RestrictTo(LIBRARY_GROUP)
    public long getBufferedPosition() {
        //return mProvider.getBufferedPosition_impl();
        return 0L;
    }

    /**
     * Get the playback speed.
     *
     * @return speed
     */
    public float getPlaybackSpeed() {
        // TODO(jaewan): implement this (b/74093080)
        return -1;
    }

    /**
     * Set the playback speed.
     */
    public void setPlaybackSpeed(float speed) {
        // TODO(jaewan): implement this (b/74093080)
    }

    /**
     * Sets the data source missing helper. Helper will be used to provide default implementation of
     * {@link MediaPlaylistAgent} when it isn't set by developer.
     * <p>
     * TODO: Fix {link DataSourceDesc}
     * Default implementation of the {@link MediaPlaylistAgent} will call helper when a
     * {@link MediaItem2} in the playlist doesn't have a {link DataSourceDesc}. This may happen
     * when
     * <ul>
     * TODO: Fix {link DataSourceDesc}
     *      <li>{@link MediaItem2} specified by {@link #setPlaylist(List, MediaMetadata2)} doesn't
     *          have {link DataSourceDesc}</li>
     *      <li>{@link MediaController2#addPlaylistItem(int, MediaItem2)} is called and accepted
     *          by {@link SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)}.
     *          In that case, an item would be added automatically without the data source.</li>
     * </ul>
     * <p>
     * If it's not set, playback wouldn't happen for the item without data source descriptor.
     * <p>
     * The helper will be run on the executor that was specified by
     * {@link Builder#setSessionCallback(Executor, SessionCallback)}.
     *
     * @param helper a data source missing helper.
     * @throws IllegalStateException when the helper is set when the playlist agent is set
     * @see #setPlaylist(List, MediaMetadata2)
     * @see SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, Command)
     * @see #COMMAND_CODE_PLAYLIST_ADD_ITEM
     * @see #COMMAND_CODE_PLAYLIST_REPLACE_ITEM
     */
    public void setOnDataSourceMissingHelper(@NonNull OnDataSourceMissingHelper helper) {
        //mProvider.setOnDataSourceMissingHelper_impl(helper);
    }

    /**
     * Clears the data source missing helper.
     *
     * @see #setOnDataSourceMissingHelper(OnDataSourceMissingHelper)
     */
    public void clearOnDataSourceMissingHelper() {
        //mProvider.clearOnDataSourceMissingHelper_impl();
    }

    /**
     * Returns the playlist from the {@link MediaPlaylistAgent}.
     * <p>
     * This list may differ with the list that was specified with
     * {@link #setPlaylist(List, MediaMetadata2)} depending on the {@link MediaPlaylistAgent}
     * implementation. Use media items returned here for other playlist agent APIs such as
     * {@link MediaPlaylistAgent#skipToPlaylistItem(MediaItem2)}.
     *
     * @return playlist
     * @see MediaPlaylistAgent#getPlaylist()
     * @see SessionCallback#onPlaylistChanged(
     *          MediaSession2, MediaPlaylistAgent, List, MediaMetadata2)
     */
    public List<MediaItem2> getPlaylist() {
        //return mProvider.getPlaylist_impl();
        return null;
    }

    /**
     * Sets a list of {@link MediaItem2} to the {@link MediaPlaylistAgent}. Ensure uniqueness of
     * each {@link MediaItem2} in the playlist so the session can uniquely identity individual
     * items.
     * <p>
     * This may be an asynchronous call, and {@link MediaPlaylistAgent} may keep the copy of the
     * list. Wait for {@link SessionCallback#onPlaylistChanged(MediaSession2, MediaPlaylistAgent,
     * List, MediaMetadata2)} to know the operation finishes.
     * <p>
     * TODO: Fix {link DataSourceDesc}
     * You may specify a {@link MediaItem2} without {link DataSourceDesc}. In that case,
     * {@link MediaPlaylistAgent} has responsibility to dynamically query {link DataSourceDesc}
     * when such media item is ready for preparation or play. Default implementation needs
     * {@link OnDataSourceMissingHelper} for such case.
     *
     * @param list A list of {@link MediaItem2} objects to set as a play list.
     * @throws IllegalArgumentException if given list is {@code null}, or has duplicated media
     * items.
     * @see MediaPlaylistAgent#setPlaylist(List, MediaMetadata2)
     * @see SessionCallback#onPlaylistChanged(
     *          MediaSession2, MediaPlaylistAgent, List, MediaMetadata2)
     * @see #setOnDataSourceMissingHelper
     */
    public void setPlaylist(@NonNull List<MediaItem2> list, @Nullable MediaMetadata2 metadata) {
        //mProvider.setPlaylist_impl(list, metadata);
    }

    /**
     * Skips to the item in the playlist.
     * <p>
     * This calls {@link MediaPlaylistAgent#skipToPlaylistItem(MediaItem2)} and the behavior depends
     * on the playlist agent implementation, especially with the shuffle/repeat mode.
     *
     * @param item The item in the playlist you want to play
     * @see #getShuffleMode()
     * @see #getRepeatMode()
     */
    public void skipToPlaylistItem(@NonNull MediaItem2 item) {
        //mProvider.skipToPlaylistItem_impl(item);
    }

    /**
     * Skips to the previous item.
     * <p>
     * This calls {@link MediaPlaylistAgent#skipToPreviousItem()} and the behavior depends on the
     * playlist agent implementation, especially with the shuffle/repeat mode.
     *
     * @see #getShuffleMode()
     * @see #getRepeatMode()
     **/
    public void skipToPreviousItem() {
        //mProvider.skipToPreviousItem_impl();
    }

    /**
     * Skips to the next item.
     * <p>
     * This calls {@link MediaPlaylistAgent#skipToNextItem()} and the behavior depends on the
     * playlist agent implementation, especially with the shuffle/repeat mode.
     *
     * @see #getShuffleMode()
     * @see #getRepeatMode()
     */
    public void skipToNextItem() {
        //mProvider.skipToNextItem_impl();
    }

    /**
     * Gets the playlist metadata from the {@link MediaPlaylistAgent}.
     *
     * @return the playlist metadata
     */
    public MediaMetadata2 getPlaylistMetadata() {
        //return mProvider.getPlaylistMetadata_impl();
        return null;
    }

    /**
     * Adds the media item to the playlist at position index.
     * <p>
     * This will not change the currently playing media item.
     * If index is less than or equal to the current index of the play list,
     * the current index of the play list will be incremented correspondingly.
     *
     * @param index the index you want to add
     * @param item the media item you want to add
     */
    public void addPlaylistItem(int index, @NonNull MediaItem2 item) {
        //mProvider.addPlaylistItem_impl(index, item);
    }

    /**
     * Removes the media item in the playlist.
     * <p>
     * If the item is the currently playing item of the playlist, current playback
     * will be stopped and playback moves to next source in the list.
     *
     * @param item the media item you want to add
     */
    public void removePlaylistItem(@NonNull MediaItem2 item) {
        //mProvider.removePlaylistItem_impl(item);
    }

    /**
     * Replaces the media item at index in the playlist. This can be also used to update metadata of
     * an item.
     *
     * @param index the index of the item to replace
     * @param item the new item
     */
    public void replacePlaylistItem(int index, @NonNull MediaItem2 item) {
        //mProvider.replacePlaylistItem_impl(index, item);
    }

    /**
     * Return currently playing media item.
     *
     * @return currently playing media item
     */
    public MediaItem2 getCurrentMediaItem() {
        // TODO(jaewan): Rename provider, and implement (b/74316764)
        //return mProvider.getCurrentPlaylistItem_impl();
        return null;
    }

    /**
     * Updates the playlist metadata to the {@link MediaPlaylistAgent}.
     *
     * @param metadata metadata of the playlist
     */
    public void updatePlaylistMetadata(@Nullable MediaMetadata2 metadata) {
        //mProvider.updatePlaylistMetadata_impl(metadata);
    }

    /**
     * Gets the repeat mode from the {@link MediaPlaylistAgent}.
     *
     * @return repeat mode
     * @see MediaPlaylistAgent#REPEAT_MODE_NONE
     * @see MediaPlaylistAgent#REPEAT_MODE_ONE
     * @see MediaPlaylistAgent#REPEAT_MODE_ALL
     * @see MediaPlaylistAgent#REPEAT_MODE_GROUP
     */
    public @RepeatMode int getRepeatMode() {
        //return mProvider.getRepeatMode_impl();
        return MediaPlaylistAgent.REPEAT_MODE_NONE;
    }

    /**
     * Sets the repeat mode to the {@link MediaPlaylistAgent}.
     *
     * @param repeatMode repeat mode
     * @see MediaPlaylistAgent#REPEAT_MODE_NONE
     * @see MediaPlaylistAgent#REPEAT_MODE_ONE
     * @see MediaPlaylistAgent#REPEAT_MODE_ALL
     * @see MediaPlaylistAgent#REPEAT_MODE_GROUP
     */
    public void setRepeatMode(@RepeatMode int repeatMode) {
        //mProvider.setRepeatMode_impl(repeatMode);
    }

    /**
     * Gets the shuffle mode from the {@link MediaPlaylistAgent}.
     *
     * @return The shuffle mode
     * @see MediaPlaylistAgent#SHUFFLE_MODE_NONE
     * @see MediaPlaylistAgent#SHUFFLE_MODE_ALL
     * @see MediaPlaylistAgent#SHUFFLE_MODE_GROUP
     */
    public @ShuffleMode int getShuffleMode() {
        //return mProvider.getShuffleMode_impl();
        return MediaPlaylistAgent.SHUFFLE_MODE_NONE;
    }

    /**
     * Sets the shuffle mode to the {@link MediaPlaylistAgent}.
     *
     * @param shuffleMode The shuffle mode
     * @see MediaPlaylistAgent#SHUFFLE_MODE_NONE
     * @see MediaPlaylistAgent#SHUFFLE_MODE_ALL
     * @see MediaPlaylistAgent#SHUFFLE_MODE_GROUP
     */
    public void setShuffleMode(@ShuffleMode int shuffleMode) {
        //mProvider.setShuffleMode_impl(shuffleMode);
    }
}

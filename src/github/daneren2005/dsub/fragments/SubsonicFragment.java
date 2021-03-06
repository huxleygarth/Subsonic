/*
 This file is part of Subsonic.

 Subsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Subsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Subsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2009 (C) Sindre Mehus
 */
package github.daneren2005.dsub.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.TextView;
import github.daneren2005.dsub.R;
import github.daneren2005.dsub.activity.DownloadActivity;
import github.daneren2005.dsub.activity.SubsonicActivity;
import github.daneren2005.dsub.activity.SubsonicFragmentActivity;
import github.daneren2005.dsub.domain.Artist;
import github.daneren2005.dsub.domain.Bookmark;
import github.daneren2005.dsub.domain.Genre;
import github.daneren2005.dsub.domain.MusicDirectory;
import github.daneren2005.dsub.domain.Playlist;
import github.daneren2005.dsub.domain.PodcastEpisode;
import github.daneren2005.dsub.domain.ServerInfo;
import github.daneren2005.dsub.domain.Share;
import github.daneren2005.dsub.service.DownloadFile;
import github.daneren2005.dsub.service.DownloadService;
import github.daneren2005.dsub.service.MediaStoreService;
import github.daneren2005.dsub.service.MusicService;
import github.daneren2005.dsub.service.MusicServiceFactory;
import github.daneren2005.dsub.service.OfflineException;
import github.daneren2005.dsub.service.ServerTooOldException;
import github.daneren2005.dsub.util.Constants;
import github.daneren2005.dsub.util.FileUtil;
import github.daneren2005.dsub.util.ImageLoader;
import github.daneren2005.dsub.util.ProgressListener;
import github.daneren2005.dsub.util.SilentBackgroundTask;
import github.daneren2005.dsub.util.LoadingTask;
import github.daneren2005.dsub.util.UserUtil;
import github.daneren2005.dsub.util.Util;
import github.daneren2005.dsub.view.AlbumCell;
import github.daneren2005.dsub.view.AlbumView;
import github.daneren2005.dsub.view.ArtistEntryView;
import github.daneren2005.dsub.view.ArtistView;
import github.daneren2005.dsub.view.PlaylistSongView;
import github.daneren2005.dsub.view.SongView;
import github.daneren2005.dsub.view.UpdateView;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import static github.daneren2005.dsub.domain.MusicDirectory.Entry;

public class SubsonicFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {
	private static final String TAG = SubsonicFragment.class.getSimpleName();
	private static int TAG_INC = 10;
	private int tag;
	
	protected SubsonicActivity context;
	protected CharSequence title = null;
	protected CharSequence subtitle = null;
	protected View rootView;
	protected boolean primaryFragment = false;
	protected boolean secondaryFragment = false;
	protected boolean invalidated = false;
	protected static Random random = new Random();
	protected GestureDetector gestureScanner;
	protected Share share;
	protected boolean artist = false;
	protected boolean artistOverride = false;
	protected SwipeRefreshLayout refreshLayout;
	protected boolean firstRun;
	
	public SubsonicFragment() {
		super();
		tag = TAG_INC++;
	}

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);

		if(bundle != null) {
			String name = bundle.getString(Constants.FRAGMENT_NAME);
			if(name != null) {
				title = name;
			}
		}
		firstRun = true;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putString(Constants.FRAGMENT_NAME, title.toString());
	}

	@Override
	public void onResume() {
		super.onResume();
		if(firstRun) {
			firstRun = false;
		} else {
			UpdateView.triggerUpdate();
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		context = (SubsonicActivity)activity;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_shuffle:
				onShuffleRequested();
				return true;
			case R.id.menu_search:
				context.onSearchRequested();
				return true;
			case R.id.menu_exit:
				exit();
				return true;
		}

		return false;
	}
	
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo, Object selected) {
		MenuInflater inflater = context.getMenuInflater();

		if(selected instanceof Entry) {
			Entry entry = (Entry) selected;
			if(entry instanceof PodcastEpisode && !entry.isVideo()) {
				if(Util.isOffline(context)) {
					inflater.inflate(R.menu.select_podcast_episode_context_offline, menu);
				}
				else {
					inflater.inflate(R.menu.select_podcast_episode_context, menu);
					
					if(entry.getBookmark() == null) {
						menu.removeItem(R.id.bookmark_menu_delete);
					}
				}
			}
			else if (entry.isDirectory()) {
				if(Util.isOffline(context)) {
					inflater.inflate(R.menu.select_album_context_offline, menu);
				}
				else {
					inflater.inflate(R.menu.select_album_context, menu);

					if(Util.isTagBrowsing(context)) {
						menu.removeItem(R.id.menu_rate);
					}
				}
				menu.findItem(entry.isDirectory() ? R.id.album_menu_star : R.id.song_menu_star).setTitle(entry.isStarred() ? R.string.common_unstar : R.string.common_star);
			} else if(!entry.isVideo()) {
				if(Util.isOffline(context)) {
					inflater.inflate(R.menu.select_song_context_offline, menu);
				}
				else {
					inflater.inflate(R.menu.select_song_context, menu);
					
					if(entry.getBookmark() == null) {
						menu.removeItem(R.id.bookmark_menu_delete);
					}
				}
				menu.findItem(entry.isDirectory() ? R.id.album_menu_star : R.id.song_menu_star).setTitle(entry.isStarred() ? R.string.common_unstar : R.string.common_star);
			} else {
				if(Util.isOffline(context)) {
					inflater.inflate(R.menu.select_video_context_offline, menu);
				}
				else {
					inflater.inflate(R.menu.select_video_context, menu);
				}
			}
		} else if(selected instanceof Artist) {
			Artist artist = (Artist) selected;
			if(Util.isOffline(context)) {
				inflater.inflate(R.menu.select_artist_context_offline, menu);
			}
			else {
				inflater.inflate(R.menu.select_artist_context, menu);

				menu.findItem(R.id.artist_menu_star).setTitle(artist.isStarred() ? R.string.common_unstar : R.string.common_star);
			}
		}

		hideMenuItems(menu, (AdapterView.AdapterContextMenuInfo) menuInfo);
	}

	protected void hideMenuItems(ContextMenu menu, AdapterView.AdapterContextMenuInfo info) {
		if(!ServerInfo.checkServerVersion(context, "1.8")) {
			menu.setGroupVisible(R.id.server_1_8, false);
			menu.setGroupVisible(R.id.hide_star, false);
		}
		if(!ServerInfo.checkServerVersion(context, "1.9")) {
			menu.setGroupVisible(R.id.server_1_9, false);
		}
		if(!ServerInfo.checkServerVersion(context, "1.10.1")) {
			menu.setGroupVisible(R.id.server_1_10, false);
		}

		SharedPreferences prefs = Util.getPreferences(context);
		if(!prefs.getBoolean(Constants.PREFERENCES_KEY_MENU_PLAY_NEXT, true)) {
			menu.setGroupVisible(R.id.hide_play_next, false);
		}
		if(!prefs.getBoolean(Constants.PREFERENCES_KEY_MENU_PLAY_LAST, true)) {
			menu.setGroupVisible(R.id.hide_play_last, false);
		}
		if(!prefs.getBoolean(Constants.PREFERENCES_KEY_MENU_STAR, true)) {
			menu.setGroupVisible(R.id.hide_star, false);
		}
		if(!prefs.getBoolean(Constants.PREFERENCES_KEY_MENU_SHARED, true) || !UserUtil.canShare()) {
			menu.setGroupVisible(R.id.hide_share, false);
		}
		if(!prefs.getBoolean(Constants.PREFERENCES_KEY_MENU_RATING, true)) {
			menu.setGroupVisible(R.id.hide_rating, false);
		}

		if(!Util.isOffline(context)) {
			// If we are looking at a standard song view, get downloadFile to cache what options to show
			if(info.targetView instanceof SongView) {
				SongView songView = (SongView) info.targetView;
				DownloadFile downloadFile = songView.getDownloadFile();
	
				try {
					if(downloadFile != null) {
						if(downloadFile.isWorkDone()) {
							// Remove permanent cache menu if already perma cached
							if(downloadFile.isSaved()) {
								menu.removeItem(R.id.song_menu_pin);
							}
	
							// Remove cache option no matter what if already downloaded
							menu.removeItem(R.id.song_menu_download);
						} else {
							// Remove delete option if nothing to delete
							menu.removeItem(R.id.song_menu_delete);
						}
					}
				} catch(Exception e) {
					Log.w(TAG, "Failed to lookup downloadFile info", e);
				}
			}
			// Apply similar logic to album views
			else if(info.targetView instanceof AlbumCell || info.targetView instanceof AlbumView
					|| info.targetView instanceof ArtistView || info.targetView instanceof ArtistEntryView) {
				File folder = null;
				int id = 0;
				if(info.targetView instanceof AlbumCell) {
					folder = ((AlbumCell) info.targetView).getFile();
					id = R.id.album_menu_delete;
				} else if(info.targetView instanceof AlbumView) {
					folder = ((AlbumView) info.targetView).getFile();
					id = R.id.album_menu_delete;
				} else if(info.targetView instanceof ArtistView) {
					folder = ((ArtistView) info.targetView).getFile();
					id = R.id.artist_menu_delete;
				} else if(info.targetView instanceof ArtistEntryView) {
					folder = ((ArtistEntryView) info.targetView).getFile();
					id = R.id.artist_menu_delete;
				}
				
				try {
					if(folder != null && !folder.exists()) {
						menu.removeItem(id);
					}
				} catch(Exception e) {
					Log.w(TAG, "Failed to lookup album directory info", e);
				}
			}
		}
	}

	protected void recreateContextMenu(ContextMenu menu) {
		List<MenuItem> menuItems = new ArrayList<MenuItem>();
		for(int i = 0; i < menu.size(); i++) {
			MenuItem item = menu.getItem(i);
			if(item.isVisible()) {
				menuItems.add(item);
			}
		}
		menu.clear();
		for(int i = 0; i < menuItems.size(); i++) {
			MenuItem item = menuItems.get(i);
			menu.add(tag, item.getItemId(), Menu.NONE, item.getTitle());
		}
	}

	public boolean onContextItemSelected(MenuItem menuItem, Object selectedItem) {
		Artist artist = selectedItem instanceof Artist ? (Artist) selectedItem : null;
		Entry entry = selectedItem instanceof Entry ? (Entry) selectedItem : null;
		List<Entry> songs = new ArrayList<Entry>(1);
		songs.add(entry);

		switch (menuItem.getItemId()) {
			case R.id.artist_menu_play_now:
				downloadRecursively(artist.getId(), false, false, true, false, false);
				break;
			case R.id.artist_menu_play_shuffled:
				downloadRecursively(artist.getId(), false, false, true, true, false);
				break;
			case R.id.artist_menu_play_next:
				downloadRecursively(artist.getId(), false, true, false, false, false, true);
				break;
			case R.id.artist_menu_play_last:
				downloadRecursively(artist.getId(), false, true, false, false, false);
				break;
			case R.id.artist_menu_download:
				downloadRecursively(artist.getId(), false, true, false, false, true);
				break;
			case R.id.artist_menu_pin:
				downloadRecursively(artist.getId(), true, true, false, false, true);
				break;
			case R.id.artist_menu_delete:
				deleteRecursively(artist);
				break;
			case R.id.artist_menu_star:
				toggleStarred(artist);
				break;
			case R.id.album_menu_play_now:
				artistOverride = true;
				downloadRecursively(entry.getId(), false, false, true, false, false);
				break;
			case R.id.album_menu_play_shuffled:
				artistOverride = true;
				downloadRecursively(entry.getId(), false, false, true, true, false);
				break;
			case R.id.album_menu_play_next:
				artistOverride = true;
				downloadRecursively(entry.getId(), false, true, false, false, false, true);
				break;
			case R.id.album_menu_play_last:
				artistOverride = true;
				downloadRecursively(entry.getId(), false, true, false, false, false);
				break;
			case R.id.album_menu_download:
				artistOverride = true;
				downloadRecursively(entry.getId(), false, true, false, false, true);
				break;
			case R.id.album_menu_pin:
				artistOverride = true;
				downloadRecursively(entry.getId(), true, true, false, false, true);
				break;
			case R.id.album_menu_star:
				toggleStarred(entry);
				break;
			case R.id.album_menu_delete:
				deleteRecursively(entry);
				break;
			case R.id.album_menu_info:
				displaySongInfo(entry);
				break;
			case R.id.album_menu_show_artist:
				showAlbumArtist((Entry) selectedItem);
				break;
			case R.id.album_menu_share:
				createShare(songs);
				break;
			case R.id.song_menu_play_now:
				playNow(songs);
				break;
			case R.id.song_menu_play_next:
				getDownloadService().download(songs, false, false, true, false);
				break;
			case R.id.song_menu_play_last:
				getDownloadService().download(songs, false, false, false, false);
				break;
			case R.id.song_menu_download:
				getDownloadService().downloadBackground(songs, false);
				break;
			case R.id.song_menu_pin:
				getDownloadService().downloadBackground(songs, true);
				break;
			case R.id.song_menu_delete:
				getDownloadService().delete(songs);
				break;
			case R.id.song_menu_add_playlist:
				addToPlaylist(songs);
				break;
			case R.id.song_menu_star:
				toggleStarred(entry);
				break;
			case R.id.song_menu_play_external:
				playExternalPlayer(entry);
				break;
			case R.id.song_menu_info:
				displaySongInfo(entry);
				break;
			case R.id.song_menu_stream_external:
				streamExternalPlayer(entry);
				break;
			case R.id.song_menu_share:
				createShare(songs);
				break;
			case R.id.song_menu_show_album:
				showAlbum((Entry) selectedItem);
				break;
			case R.id.song_menu_show_artist:
				showArtist((Entry) selectedItem);
				break;
			case R.id.bookmark_menu_delete:
				deleteBookmark(entry, null);
				break;
			case R.id.menu_rate:
				setRating(entry);
				break;
			default:
				return false;
		}

		return true;
	}
	
	public void replaceFragment(SubsonicFragment fragment) {
		replaceFragment(fragment, true);
	}
	public void replaceFragment(SubsonicFragment fragment, boolean replaceCurrent) {
		context.replaceFragment(fragment, fragment.getSupportTag(), secondaryFragment && replaceCurrent);
	}

	public int getRootId() {
		return rootView.getId();
	}

	public void setSupportTag(int tag) { this.tag = tag; }
	public void setSupportTag(String tag) { this.tag = Integer.parseInt(tag); }
	public int getSupportTag() {
		return tag;
	}
	
	public void setPrimaryFragment(boolean primary) {
		primaryFragment = primary;
		if(primary) {
			if(context != null && title != null) {
				context.setTitle(title);
				context.setSubtitle(subtitle);
			}
			if(invalidated) {
				invalidated = false;
				refresh(false);
			}
		}
	}
	public void setPrimaryFragment(boolean primary, boolean secondary) {
		setPrimaryFragment(primary);
		secondaryFragment = secondary;
	}
	public void setSecondaryFragment(boolean secondary) {
		secondaryFragment = secondary;
	}

	public void invalidate() {
		if(primaryFragment) {
			refresh(true);
		} else {
			invalidated = true;
		}
	}

	public DownloadService getDownloadService() {
		return context != null ? context.getDownloadService() : null;
	}

	protected void refresh() {
		refresh(true);
	}
	protected void refresh(boolean refresh) {

	}

	@Override
	public void onRefresh() {
		refreshLayout.setRefreshing(false);
		refresh();
	}

	protected void exit() {
		if(((Object) context).getClass() != SubsonicFragmentActivity.class) {
			Intent intent = new Intent(context, SubsonicFragmentActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			intent.putExtra(Constants.INTENT_EXTRA_NAME_EXIT, true);
			Util.startActivityWithoutTransition(context, intent);
		} else {
			context.stopService(new Intent(context, DownloadService.class));
			context.finish();
		}
	}

	public void setProgressVisible(boolean visible) {
		View view = rootView.findViewById(R.id.tab_progress);
		if (view != null) {
			view.setVisibility(visible ? View.VISIBLE : View.GONE);

			if(visible) {
				View progress = rootView.findViewById(R.id.tab_progress_spinner);
				progress.setVisibility(View.VISIBLE);
			}
		}
	}

	public void updateProgress(String message) {
		TextView view = (TextView) rootView.findViewById(R.id.tab_progress_message);
		if (view != null) {
			view.setText(message);
		}
	}

	public void setEmpty(boolean empty) {
		View view = rootView.findViewById(R.id.tab_progress);
		if(empty) {
			view.setVisibility(View.VISIBLE);

			View progress = view.findViewById(R.id.tab_progress_spinner);
			progress.setVisibility(View.GONE);

			TextView text = (TextView) view.findViewById(R.id.tab_progress_message);
			text.setText(R.string.common_empty);
		} else {
			view.setVisibility(View.GONE);
		}
	}

	protected synchronized ImageLoader getImageLoader() {
		return context.getImageLoader();
	}
	public synchronized static ImageLoader getStaticImageLoader(Context context) {
		return SubsonicActivity.getStaticImageLoader(context);
	}

	public void setTitle(CharSequence title) {
		this.title = title;
		context.setTitle(title);
	}
	public void setTitle(int title) {
		this.title = context.getResources().getString(title);
		context.setTitle(this.title);
	}
	public void setSubtitle(CharSequence title) {
		this.subtitle = title;
		context.setSubtitle(title);
	}
	public CharSequence getTitle() {
		return this.title;
	}

	protected void setupScrollList(final AbsListView listView) {
		listView.setOnScrollListener(new AbsListView.OnScrollListener() {
			@Override
			public void onScrollStateChanged(AbsListView view, int scrollState) {}

			@Override
			public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
				int topRowVerticalPosition = (listView.getChildCount() == 0) ? 0 : listView.getChildAt(0).getTop();
				refreshLayout.setEnabled(topRowVerticalPosition >= 0 && listView.getFirstVisiblePosition() == 0);
			}
		});

		refreshLayout.setColorScheme(
			R.color.holo_blue_light,
			R.color.holo_orange_light,
			R.color.holo_green_light,
			R.color.holo_red_light);
	}

	protected void warnIfStorageUnavailable() {
		if (!Util.isExternalStoragePresent()) {
			Util.toast(context, R.string.select_album_no_sdcard);
		}
	}

	protected void onShuffleRequested() {
		if(Util.isOffline(context)) {
			Intent intent = new Intent(context, DownloadActivity.class);
			intent.putExtra(Constants.INTENT_EXTRA_NAME_SHUFFLE, true);
			Util.startActivityWithoutTransition(context, intent);
			return;
		}

		View dialogView = context.getLayoutInflater().inflate(R.layout.shuffle_dialog, null);
		final EditText startYearBox = (EditText)dialogView.findViewById(R.id.start_year);
		final EditText endYearBox = (EditText)dialogView.findViewById(R.id.end_year);
		final EditText genreBox = (EditText)dialogView.findViewById(R.id.genre);
		final Button genreCombo = (Button)dialogView.findViewById(R.id.genre_combo);

		final SharedPreferences prefs = Util.getPreferences(context);
		final String oldStartYear = prefs.getString(Constants.PREFERENCES_KEY_SHUFFLE_START_YEAR, "");
		final String oldEndYear = prefs.getString(Constants.PREFERENCES_KEY_SHUFFLE_END_YEAR, "");
		final String oldGenre = prefs.getString(Constants.PREFERENCES_KEY_SHUFFLE_GENRE, "");

		boolean _useCombo = false;
		if(ServerInfo.checkServerVersion(context, "1.9.0")) {
			genreBox.setVisibility(View.GONE);
			genreCombo.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					new LoadingTask<List<Genre>>(context, true) {
						@Override
						protected List<Genre> doInBackground() throws Throwable {
							MusicService musicService = MusicServiceFactory.getMusicService(context);
							return musicService.getGenres(false, context, this);
						}

						@Override
						protected void done(final List<Genre> genres) {
							List<String> names = new ArrayList<String>();
							String blank = context.getResources().getString(R.string.select_genre_blank);
							names.add(blank);
							for(Genre genre: genres) {
								names.add(genre.getName());
							}
							final List<String> finalNames = names;

							AlertDialog.Builder builder = new AlertDialog.Builder(context);
							builder.setTitle(R.string.shuffle_pick_genre)
								.setItems(names.toArray(new CharSequence[names.size()]), new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog, int which) {
									if(which == 0) {
										genreCombo.setText("");
									} else {
										genreCombo.setText(finalNames.get(which));
									}
								}
							});
							AlertDialog dialog = builder.create();
							dialog.show();
						}

						@Override
						protected void error(Throwable error) {            	
							String msg;
							if (error instanceof OfflineException || error instanceof ServerTooOldException) {
								msg = getErrorMessage(error);
							} else {
								msg = context.getResources().getString(R.string.playlist_error) + " " + getErrorMessage(error);
							}

							Util.toast(context, msg, false);
						}
					}.execute();
				}
			});
			_useCombo = true;
		} else {
			genreCombo.setVisibility(View.GONE);
		}
		final boolean useCombo = _useCombo;

		startYearBox.setText(oldStartYear);
		endYearBox.setText(oldEndYear);
		genreBox.setText(oldGenre);
		genreCombo.setText(oldGenre);

		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(R.string.shuffle_title)
			.setView(dialogView)
			.setPositiveButton(R.string.common_ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					Intent intent = new Intent(context, DownloadActivity.class);
					intent.putExtra(Constants.INTENT_EXTRA_NAME_SHUFFLE, true);
					String genre;
					if(useCombo) {
						genre = genreCombo.getText().toString();
					} else {
						genre = genreBox.getText().toString();
					}
					String startYear = startYearBox.getText().toString();
					String endYear = endYearBox.getText().toString();

					SharedPreferences.Editor editor = prefs.edit();
					editor.putString(Constants.PREFERENCES_KEY_SHUFFLE_START_YEAR, startYear);
					editor.putString(Constants.PREFERENCES_KEY_SHUFFLE_END_YEAR, endYear);
					editor.putString(Constants.PREFERENCES_KEY_SHUFFLE_GENRE, genre);
					editor.commit();

					Util.startActivityWithoutTransition(context, intent);
				}
			})
			.setNegativeButton(R.string.common_cancel, null);
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	public void toggleStarred(Entry entry) {
		toggleStarred(entry, null);
	}
	public void toggleStarred(final Entry entry, final OnStarChange onStarChange) {
		final boolean starred = !entry.isStarred();
		entry.setStarred(starred);
		if(onStarChange != null) {
			onStarChange.starChange(starred);
		}

		new SilentBackgroundTask<Void>(context) {
			@Override
			protected Void doInBackground() throws Throwable {
				MusicService musicService = MusicServiceFactory.getMusicService(context);
				if(entry.isDirectory() && Util.isTagBrowsing(context) && !Util.isOffline(context)) {
					if(entry.isAlbum()) {
						musicService.setStarred(null, null, Arrays.asList(entry), starred, null, context);
					} else {
						musicService.setStarred(null, Arrays.asList(entry), null, starred, null, context);
					}
				} else {
					musicService.setStarred(Arrays.asList(entry), null, null, starred, null, context);
				}

				new EntryInstanceUpdater(entry) {
					@Override
					public void update(Entry found) {
						found.setStarred(starred);
					}
				}.execute();

				return null;
			}

			@Override
			protected void done(Void result) {
				// UpdateView
				Util.toast(context, context.getResources().getString(starred ? R.string.starring_content_starred : R.string.starring_content_unstarred, entry.getTitle()));
			}

			@Override
			protected void error(Throwable error) {
				Log.w(TAG, "Failed to star", error);
				entry.setStarred(!starred);
				if(onStarChange != null) {
					onStarChange.starChange(!starred);
				}

				String msg;
				if (error instanceof OfflineException || error instanceof ServerTooOldException) {
					msg = getErrorMessage(error);
				} else {
					msg = context.getResources().getString(R.string.starring_content_error, entry.getTitle()) + " " + getErrorMessage(error);
				}

				Util.toast(context, msg, false);
			}
		}.execute();
	}

	public void toggleStarred(final Artist entry) {
		final boolean starred = !entry.isStarred();
		entry.setStarred(starred);

		new SilentBackgroundTask<Void>(context) {
			@Override
			protected Void doInBackground() throws Throwable {
				MusicService musicService = MusicServiceFactory.getMusicService(context);
				if(Util.isTagBrowsing(context) && !Util.isOffline(context)) {
					musicService.setStarred(null, Arrays.asList(new Entry(entry)), null, starred, null, context);
				} else {
					musicService.setStarred(Arrays.asList(new Entry(entry)), null, null, starred, null, context);
				}
				return null;
			}

			@Override
			protected void done(Void result) {
				// UpdateView
				Util.toast(context, context.getResources().getString(starred ? R.string.starring_content_starred : R.string.starring_content_unstarred, entry.getName()));
			}

			@Override
			protected void error(Throwable error) {
				Log.w(TAG, "Failed to star", error);
				entry.setStarred(!starred);

				String msg;
				if (error instanceof OfflineException || error instanceof ServerTooOldException) {
					msg = getErrorMessage(error);
				} else {
					msg = context.getResources().getString(R.string.starring_content_error, entry.getName()) + " " + getErrorMessage(error);
				}

				Util.toast(context, msg, false);
			}
		}.execute();
	}

	protected void downloadRecursively(final String id, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background) {
		downloadRecursively(id, "", true, save, append, autoplay, shuffle, background);
	}
	protected void downloadRecursively(final String id, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background, final boolean playNext) {
		downloadRecursively(id, "", true, save, append, autoplay, shuffle, background, playNext);
	}
	protected void downloadPlaylist(final String id, final String name, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background) {
		downloadRecursively(id, name, false, save, append, autoplay, shuffle, background);
	}
	protected void downloadRecursively(final String id, final String name, final boolean isDirectory, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background) {
		downloadRecursively(id, name, isDirectory, save, append, autoplay, shuffle, background, false);
	}
	protected void downloadRecursively(final String id, final String name, final boolean isDirectory, final boolean save, final boolean append, final boolean autoplay, final boolean shuffle, final boolean background, final boolean playNext) {
		new RecursiveLoader(context) {
			@Override
			protected Boolean doInBackground() throws Throwable {
				musicService = MusicServiceFactory.getMusicService(context);
				MusicDirectory root;
				if(share != null) {
					root = share.getMusicDirectory();
				}
				else if(isDirectory) {
					if(id != null) {
						root = getMusicDirectory(id, name, false, musicService, this);
					} else {
						root = musicService.getStarredList(context, this);
					}
				}
				else {
					root = musicService.getPlaylist(true, id, name, context, this);
				}

				if(shuffle) {
					Collections.shuffle(root.getChildren());
				}

				songs = new LinkedList<Entry>();
				getSongsRecursively(root, songs);

				DownloadService downloadService = getDownloadService();
				boolean transition = false;
				if (!songs.isEmpty() && downloadService != null) {
					// Conditions for a standard play now operation
					if(!append && !save && autoplay && !playNext && !shuffle && !background) {
						playNowOverride = true;
						return false;
					}
					
					if (!append && !background) {
						downloadService.clear();
					}
					if(!background) {
						downloadService.download(songs, save, autoplay, playNext, false);
						if(!append) {
							transition = true;
						}
					}
					else {
						downloadService.downloadBackground(songs, save);
					}
				}
				artistOverride = false;

				return transition;
			}
		}.execute();
	}

	protected void downloadRecursively(final List<Entry> albums, final boolean shuffle, final boolean append) {
		new RecursiveLoader(context) {
			@Override
			protected Boolean doInBackground() throws Throwable {
				musicService = MusicServiceFactory.getMusicService(context);

				if(shuffle) {
					Collections.shuffle(albums);
				}

				songs = new LinkedList<Entry>();
				MusicDirectory root = new MusicDirectory();
				root.addChildren(albums);
				getSongsRecursively(root, songs);

				DownloadService downloadService = getDownloadService();
				boolean transition = false;
				if (!songs.isEmpty() && downloadService != null) {
					// Conditions for a standard play now operation
					if(!append && !shuffle) {
						playNowOverride = true;
						return false;
					}

					if (!append) {
						downloadService.clear();
					}

					downloadService.download(songs, false, true, false, false);
					if(!append) {
						transition = true;
					}
				}
				artistOverride = false;

				return transition;
			}
		}.execute();
	}

	protected MusicDirectory getMusicDirectory(String id, String name, boolean refresh, MusicService service, ProgressListener listener) throws Exception {
		return getMusicDirectory(id, name, refresh, false, service, listener);
	}
	protected MusicDirectory getMusicDirectory(String id, String name, boolean refresh, boolean forceArtist, MusicService service, ProgressListener listener) throws Exception {
		if(Util.isTagBrowsing(context) && !Util.isOffline(context)) {
			if(artist && !artistOverride || forceArtist) {
				return service.getArtist(id, name, refresh, context, listener);
			} else {
				return service.getAlbum(id, name, refresh, context, listener);
			}
		} else {
			return service.getMusicDirectory(id, name, refresh, context, listener);
		}
	}

	protected void addToPlaylist(final List<Entry> songs) {
		if(songs.isEmpty()) {
			Util.toast(context, "No songs selected");
			return;
		}

		new LoadingTask<List<Playlist>>(context, true) {
			@Override
			protected List<Playlist> doInBackground() throws Throwable {
				MusicService musicService = MusicServiceFactory.getMusicService(context);
				List<Playlist> playlists = new ArrayList<Playlist>();
				playlists.addAll(musicService.getPlaylists(false, context, this));
				
				// Iterate through and remove all non owned public playlists
				Iterator<Playlist> it = playlists.iterator();
				while(it.hasNext()) {
					Playlist playlist = it.next();
					if(playlist.getPublic() == true && playlist.getId().indexOf(".m3u") == -1 && !UserUtil.getCurrentUsername(context).equals(playlist.getOwner())) {
						it.remove();
					}
				}
				
				return playlists;
			}

			@Override
			protected void done(final List<Playlist> playlists) {
				// Create adapter to show playlists
				Playlist createNew = new Playlist("-1", context.getResources().getString(R.string.playlist_create_new));
				playlists.add(0, createNew);
				ArrayAdapter playlistAdapter = new ArrayAdapter<Playlist>(context, R.layout.basic_count_item, playlists) {
					@Override
					public View getView(int position, View convertView, ViewGroup parent) {
						Playlist playlist = getItem(position);
						
						// Create new if not getting a convert view to use
						PlaylistSongView view;
						if(convertView instanceof PlaylistSongView) {
							view = (PlaylistSongView) convertView;
						} else {
							view =  new PlaylistSongView(context);
						}

						view.setObject(playlist, songs);

						return view;
					}
				};

				AlertDialog.Builder builder = new AlertDialog.Builder(context);
				builder.setTitle(R.string.playlist_add_to)
					.setAdapter(playlistAdapter, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							if (which > 0) {
								addToPlaylist(playlists.get(which), songs);
							} else {
								createNewPlaylist(songs, false);
							}
						}
					});
				AlertDialog dialog = builder.create();
				dialog.show();
			}

			@Override
			protected void error(Throwable error) {            	
				String msg;
				if (error instanceof OfflineException || error instanceof ServerTooOldException) {
					msg = getErrorMessage(error);
				} else {
					msg = context.getResources().getString(R.string.playlist_error) + " " + getErrorMessage(error);
				}

				Util.toast(context, msg, false);
			}
		}.execute();
	}

	private void addToPlaylist(final Playlist playlist, final List<Entry> songs) {
		new SilentBackgroundTask<Void>(context) {
			@Override
			protected Void doInBackground() throws Throwable {
				MusicService musicService = MusicServiceFactory.getMusicService(context);
				musicService.addToPlaylist(playlist.getId(), songs, context, null);
				return null;
			}

			@Override
			protected void done(Void result) {
				Util.toast(context, context.getResources().getString(R.string.updated_playlist, songs.size(), playlist.getName()));
			}

			@Override
			protected void error(Throwable error) {            	
				String msg;
				if (error instanceof OfflineException || error instanceof ServerTooOldException) {
					msg = getErrorMessage(error);
				} else {
					msg = context.getResources().getString(R.string.updated_playlist_error, playlist.getName()) + " " + getErrorMessage(error);
				}

				Util.toast(context, msg, false);
			}
		}.execute();
	}
	
	protected void createNewPlaylist(final List<Entry> songs, final boolean getSuggestion) {
		View layout = context.getLayoutInflater().inflate(R.layout.save_playlist, null);
		final EditText playlistNameView = (EditText) layout.findViewById(R.id.save_playlist_name);
		final CheckBox overwriteCheckBox = (CheckBox) layout.findViewById(R.id.save_playlist_overwrite);
		if(getSuggestion) {
			String playlistName = (getDownloadService() != null) ? getDownloadService().getSuggestedPlaylistName() : null;
			if (playlistName != null) {
				playlistNameView.setText(playlistName);
				try {
					if(ServerInfo.checkServerVersion(context, "1.8.0") && Integer.parseInt(getDownloadService().getSuggestedPlaylistId()) != -1) {
						overwriteCheckBox.setChecked(true);
						overwriteCheckBox.setVisibility(View.VISIBLE);
					}
				} catch(Exception e) {
					Log.d(TAG, "Playlist id isn't a integer, probably MusicCabinet");
				}
			} else {
				DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
				playlistNameView.setText(dateFormat.format(new Date()));
			}
		} else {
			DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
			playlistNameView.setText(dateFormat.format(new Date()));
		}

		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(R.string.download_playlist_title)
			.setMessage(R.string.download_playlist_name)
			.setView(layout)
			.setPositiveButton(R.string.common_save, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					String playlistName = String.valueOf(playlistNameView.getText());
					if(overwriteCheckBox.isChecked()) {
						overwritePlaylist(songs, playlistName, getDownloadService().getSuggestedPlaylistId());
					} else {
						createNewPlaylist(songs, playlistName);
						
						if(getSuggestion) {
							DownloadService downloadService = getDownloadService();
							if(downloadService != null) {
								downloadService.setSuggestedPlaylistName(playlistName, null);
							}
						}
					}
				}
			})
			.setNegativeButton(R.string.common_cancel, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					dialog.cancel();
				}
			})
			.setCancelable(true);
		
		AlertDialog dialog = builder.create();
		dialog.show();
	}
	private void createNewPlaylist(final List<Entry> songs, final String name) {
		new SilentBackgroundTask<Void>(context) {
			@Override
			protected Void doInBackground() throws Throwable {
				MusicService musicService = MusicServiceFactory.getMusicService(context);
				musicService.createPlaylist(null, name, songs, context, null);
				return null;
			}

			@Override
			protected void done(Void result) {
				Util.toast(context, R.string.download_playlist_done);
			}

			@Override
			protected void error(Throwable error) {
				String msg = context.getResources().getString(R.string.download_playlist_error) + " " + getErrorMessage(error);
				Util.toast(context, msg);
			}
		}.execute();
	}
	private void overwritePlaylist(final List<Entry> songs, final String name, final String id) {
		new SilentBackgroundTask<Void>(context) {
			@Override
			protected Void doInBackground() throws Throwable {
				MusicService musicService = MusicServiceFactory.getMusicService(context);
				MusicDirectory playlist = musicService.getPlaylist(true, id, name, context, null);
				List<Entry> toDelete = playlist.getChildren();
				musicService.overwritePlaylist(id, name, toDelete.size(), songs, context, null);
				return null;
			}

			@Override
			protected void done(Void result) {
				Util.toast(context, R.string.download_playlist_done);
			}

			@Override
			protected void error(Throwable error) {            	
				String msg;
				if (error instanceof OfflineException || error instanceof ServerTooOldException) {
					msg = getErrorMessage(error);
				} else {
					msg = context.getResources().getString(R.string.download_playlist_error) + " " + getErrorMessage(error);
				}

				Util.toast(context, msg, false);
			}
		}.execute();
	}

	public void displaySongInfo(final Entry song) {
		Integer duration = null;
		Integer bitrate = null;
		String format = null;
		long size = 0;
		if(!song.isDirectory()) {
			try {
				DownloadFile downloadFile = new DownloadFile(context, song, false);
				File file = downloadFile.getCompleteFile();
				if(file.exists()) {
					MediaMetadataRetriever metadata = new MediaMetadataRetriever();
					metadata.setDataSource(file.getAbsolutePath());
					
					String tmp = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
					duration = Integer.parseInt((tmp != null) ? tmp : "0") / 1000;
					format = FileUtil.getExtension(file.getName());
					size = file.length();
					
					// If no duration try to read bitrate tag
					if(duration == null) {
						tmp = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
						bitrate = Integer.parseInt((tmp != null) ? tmp : "0") / 1000;
					} else {
						// Otherwise do a calculation for it
						// Divide by 1000 so in kbps
						bitrate = (int) (size / duration) / 1000 * 8;
					}
	
					if(Util.isOffline(context)) {
						song.setGenre(metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE));
						String year = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
						song.setYear(Integer.parseInt((year != null) ? year : "0"));
					}
				}
			} catch(Exception e) {
				Log.i(TAG, "Device doesn't properly support MediaMetadataRetreiver");
			}
		}

		String msg = "";
		if(song instanceof PodcastEpisode) {
			msg += "Podcast: " + song.getArtist() + "\nStatus: " + ((PodcastEpisode)song).getStatus();
		} else if(!song.isVideo()) {
			if(song.getArtist() != null && !"".equals(song.getArtist())) {
				msg += "Artist: " + song.getArtist();
			}
			if(song.getAlbum() != null && !"".equals(song.getAlbum())) {
				msg += "\nAlbum: " + song.getAlbum();
			}
		}
		if(song.getTrack() != null && song.getTrack() != 0) {
			msg += "\nTrack: " + song.getTrack();
		}
		if(song.getGenre() != null && !"".equals(song.getGenre())) {
			msg += "\nGenre: " + song.getGenre();
		}
		if(song.getYear() != null && song.getYear() != 0) {
			msg += "\nYear: " + song.getYear();
		}
		if(!Util.isOffline(context) && song.getSuffix() != null) {
			msg += "\nServer Format: " + song.getSuffix();
			if(song.getBitRate() != null && song.getBitRate() != 0) {
				msg += "\nServer Bitrate: " + song.getBitRate() + " kbps";
			}
		}
		if(format != null && !"".equals(format)) {
			msg += "\nCached Format: " + format;
		}
		if(bitrate != null && bitrate != 0) {
			msg += "\nCached Bitrate: " + bitrate + " kbps";
		}
		if(size != 0) {
			msg += "\nSize: " + Util.formatLocalizedBytes(size, context);
		}
		if(song.getDuration() != null && song.getDuration() != 0) {
			msg += "\nLength: " + Util.formatDuration(song.getDuration());
		}
		if(song.getBookmark() != null) {
			msg += "\nBookmark Position: " + Util.formatDuration(song.getBookmark().getPosition() / 1000);
		}
		if(song.getRating() != 0) {
			msg += "\nRating: " + song.getRating() + " stars";
		}
		if(song instanceof PodcastEpisode) {
			msg += "\n\nDescription: " + song.getAlbum();
		}

		Util.info(context, song.getTitle(), msg);
	}
	
	protected void playVideo(Entry entry) {
		if(entryExists(entry)) {
			playExternalPlayer(entry);
		} else {
			streamExternalPlayer(entry);
		}
	}

	protected void playWebView(Entry entry) {
		int maxBitrate = Util.getMaxVideoBitrate(context);

		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setData(Uri.parse(MusicServiceFactory.getMusicService(context).getVideoUrl(maxBitrate, context, entry.getId())));

		startActivity(intent);
	}
	protected void playExternalPlayer(Entry entry) {
		if(!entryExists(entry)) {
			Util.toast(context, R.string.download_need_download);
		} else {
			DownloadFile check = new DownloadFile(context, entry, false);
			File file = check.getCompleteFile();

			Intent intent = new Intent(Intent.ACTION_VIEW);
			intent.setDataAndType(Uri.fromFile(file), "video/*");
			intent.putExtra(Intent.EXTRA_TITLE, entry.getTitle());

			List<ResolveInfo> intents = context.getPackageManager()
				.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
			if(intents != null && intents.size() > 0) {
				startActivity(intent);
			}else {
				Util.toast(context, R.string.download_no_streaming_player);
			}
		}
	}
	protected void streamExternalPlayer(Entry entry) {
		String videoPlayerType = Util.getVideoPlayerType(context);
		if("flash".equals(videoPlayerType)) {
			playWebView(entry);
		} else if("hls".equals(videoPlayerType)) {
			streamExternalPlayer(entry, "hls");
		} else if("raw".equals(videoPlayerType)) {
			streamExternalPlayer(entry, "raw");
		} else {
			streamExternalPlayer(entry, entry.getTranscodedSuffix());
		}
	}
	protected void streamExternalPlayer(Entry entry, String format) {
		try {
			int maxBitrate = Util.getMaxVideoBitrate(context);

			Intent intent = new Intent(Intent.ACTION_VIEW);
			if("hls".equals(format)) {
				intent.setDataAndType(Uri.parse(MusicServiceFactory.getMusicService(context).getHlsUrl(entry.getId(), maxBitrate, context)), "application/x-mpegURL");
			} else {
				intent.setDataAndType(Uri.parse(MusicServiceFactory.getMusicService(context).getVideoStreamUrl(format, maxBitrate, context, entry.getId())), "video/*");
			}
			intent.putExtra("title", entry.getTitle());

			List<ResolveInfo> intents = context.getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
			if(intents != null && intents.size() > 0) {
				startActivity(intent);
			} else {
				Util.toast(context, R.string.download_no_streaming_player);
			}
		} catch(Exception error) {
			String msg;
			if (error instanceof OfflineException || error instanceof ServerTooOldException) {
				msg = error.getMessage();
			} else {
				msg = context.getResources().getString(R.string.download_no_streaming_player) + " " + error.getMessage();
			}

			Util.toast(context, msg, false);
		}
	}

	protected boolean entryExists(Entry entry) {
		DownloadFile check = new DownloadFile(context, entry, false);
		return check.isCompleteFileAvailable();
	}

	public void deleteRecursively(Artist artist) {
		deleteRecursively(FileUtil.getArtistDirectory(context, artist));
	}
	
	public void deleteRecursively(Entry album) {
		deleteRecursively(FileUtil.getAlbumDirectory(context, album));

	}
	public void deleteRecursively(final File dir) {
		if(dir == null) {
			return;
		}

		new LoadingTask<Void>(context) {
			@Override
			protected Void doInBackground() throws Throwable {
				MediaStoreService mediaStore = new MediaStoreService(context);
				FileUtil.recursiveDelete(dir, mediaStore);
				return null;
			}

			@Override
			protected void done(Void result) {
				if(Util.isOffline(context)) {
					refresh();
				} else {
					UpdateView.triggerUpdate();
				}
			}
		}.execute();
	}

	public void showAlbumArtist(Entry entry) {
		SubsonicFragment fragment = new SelectDirectoryFragment();
		Bundle args = new Bundle();
		if(Util.isTagBrowsing(context)) {
			args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.getArtistId());
		} else {
			args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.getParent());
		}
		args.putString(Constants.INTENT_EXTRA_NAME_NAME, entry.getArtist());
		args.putBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, true);
		fragment.setArguments(args);

		replaceFragment(fragment, true);
	}
	public void showArtist(Entry entry) {
		SubsonicFragment fragment = new SelectDirectoryFragment();
		Bundle args = new Bundle();
		if(Util.isTagBrowsing(context)) {
			args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.getArtistId());
		} else {
			if(entry.getGrandParent() == null) {
				args.putString(Constants.INTENT_EXTRA_NAME_CHILD_ID, entry.getParent());
			} else {
				args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.getGrandParent());
			}
		}
		args.putString(Constants.INTENT_EXTRA_NAME_NAME, entry.getArtist());
		args.putBoolean(Constants.INTENT_EXTRA_NAME_ARTIST, true);
		fragment.setArguments(args);

		replaceFragment(fragment, true);
	}
	public void showAlbum(Entry entry) {
		SubsonicFragment fragment = new SelectDirectoryFragment();
		Bundle args = new Bundle();
		if(Util.isTagBrowsing(context)) {
			args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.getAlbumId());
		} else {
			args.putString(Constants.INTENT_EXTRA_NAME_ID, entry.getParent());
		}
		args.putString(Constants.INTENT_EXTRA_NAME_NAME, entry.getAlbum());
		fragment.setArguments(args);

		replaceFragment(fragment, true);
	}

	public void createShare(final List<Entry> entries) {
		new LoadingTask<List<Share>>(context, true) {
			@Override
			protected List<Share> doInBackground() throws Throwable {
				List<String> ids = new ArrayList<String>(entries.size());
				for(Entry entry: entries) {
					ids.add(entry.getId());
				}

				MusicService musicService = MusicServiceFactory.getMusicService(context);
				return musicService.createShare(ids, null, 0L, context, this);
			}

			@Override
			protected void done(final List<Share> shares) {
				if(shares.size() > 0) {
					Share share = shares.get(0);
					shareExternal(share);
				} else {
					Util.toast(context, context.getResources().getString(R.string.playlist_error), false);
				}
			}

			@Override
			protected void error(Throwable error) {
				String msg;
				if (error instanceof OfflineException || error instanceof ServerTooOldException) {
					msg = getErrorMessage(error);
				} else {
					msg = context.getResources().getString(R.string.playlist_error) + " " + getErrorMessage(error);
				}

				Util.toast(context, msg, false);
			}
		}.execute();
	}
	public void shareExternal(Share share) {
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("text/plain");
		intent.putExtra(Intent.EXTRA_TEXT, share.getUrl());
		context.startActivity(Intent.createChooser(intent, context.getResources().getString(R.string.share_via)));
	}
	
	public GestureDetector getGestureDetector() {
		return gestureScanner;
	}

	protected void playBookmark(List<Entry> songs, Entry song) {
		playBookmark(songs, song, null, null);
	}
	protected void playBookmark(final List<Entry> songs, final Entry song, final String playlistName, final String playlistId) {
		final Integer position = song.getBookmark().getPosition();

		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(R.string.bookmark_resume_title)
				.setMessage(getResources().getString(R.string.bookmark_resume, song.getTitle(), Util.formatDuration(position / 1000)))
				.setPositiveButton(R.string.bookmark_action_resume, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						playNow(songs, song, position);
					}
				})
				.setNegativeButton(R.string.bookmark_action_start_over, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int id) {
						final Bookmark oldBookmark = song.getBookmark();
						song.setBookmark(null);
						
						new SilentBackgroundTask<Void>(context) {
							@Override
							protected Void doInBackground() throws Throwable {
								MusicService musicService = MusicServiceFactory.getMusicService(context);
								musicService.deleteBookmark(song, context, null);

								return null;
							}

							@Override
							protected void error(Throwable error) {
								song.setBookmark(oldBookmark);
								
								String msg;
								if (error instanceof OfflineException || error instanceof ServerTooOldException) {
									msg = getErrorMessage(error);
								} else {
									msg = context.getResources().getString(R.string.bookmark_deleted_error, song.getTitle()) + " " + getErrorMessage(error);
								}

								Util.toast(context, msg, false);
							}
						}.execute();

						playNow(songs, 0);
					}
				});
		AlertDialog dialog = builder.create();
		dialog.show();
	}
	
	protected void playNow(List<Entry> entries) {
		playNow(entries, null, null);
	}
	protected void playNow(List<Entry> entries, String playlistName, String playlistId) {
		Entry bookmark = null;
		for(Entry entry: entries) {
			if(entry.getBookmark() != null) {
				bookmark = entry;
				break;
			}
		}
		
		// If no bookmark found, just play from start
		if(bookmark == null) {
			playNow(entries, 0, playlistName, playlistId);
		} else {
			// If bookmark found, then give user choice to start from there or to start over
			playBookmark(entries, bookmark, playlistName, playlistId);
		}
	}
	protected void playNow(List<Entry> entries, int position) {
		playNow(entries, position, null, null);
	}
	protected void playNow(List<Entry> entries, int position, String playlistName, String playlistId) {
		Entry selected = entries.isEmpty() ? null : entries.get(0);
		playNow(entries, selected, position, playlistName, playlistId);
	}
	protected void playNow(List<Entry> entries, Entry song, int position) {
		playNow(entries, song, position, null, null);
	}
	protected void playNow(final List<Entry> entries, final Entry song, final int position, final String playlistName, final String playlistId) {
		new LoadingTask<Void>(context) {
			@Override
			protected Void doInBackground() throws Throwable {
				DownloadService downloadService = getDownloadService();
				if(downloadService == null) {
					return null;
				}
				
				downloadService.clear();
				downloadService.download(entries, false, true, true, false, entries.indexOf(song), position);
				downloadService.setSuggestedPlaylistName(playlistName, playlistId);

				return null;
			}
			
			@Override
			protected void done(Void result) {
				Util.startActivityWithoutTransition(context, DownloadActivity.class);
			}
		}.execute();
	}

	protected void deleteBookmark(final MusicDirectory.Entry entry, final ArrayAdapter adapter) {
		Util.confirmDialog(context, R.string.bookmark_delete_title, entry.getTitle(), new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				final Bookmark oldBookmark = entry.getBookmark();
				entry.setBookmark(null);
				
				new LoadingTask<Void>(context, false) {
					@Override
					protected Void doInBackground() throws Throwable {
						MusicService musicService = MusicServiceFactory.getMusicService(context);
						musicService.deleteBookmark(entry, context, null);
						
						new EntryInstanceUpdater(entry) {
							@Override
							public void update(Entry found) {
								found.setBookmark(null);
							}
						}.execute();

						return null;
					}

					@Override
					protected void done(Void result) {
						if (adapter != null) {
							adapter.remove(entry);
							adapter.notifyDataSetChanged();
						}
						Util.toast(context, context.getResources().getString(R.string.bookmark_deleted, entry.getTitle()));
					}

					@Override
					protected void error(Throwable error) {
						entry.setBookmark(oldBookmark);
						
						String msg;
						if (error instanceof OfflineException || error instanceof ServerTooOldException) {
							msg = getErrorMessage(error);
						} else {
							msg = context.getResources().getString(R.string.bookmark_deleted_error, entry.getTitle()) + " " + getErrorMessage(error);
						}

						Util.toast(context, msg, false);
					}
				}.execute();
			}
		});
	}

	protected void setRating(Entry entry) {
		setRating(entry, null);
	}
	protected void setRating(final Entry entry, final OnRatingChange onRatingChange) {
		View layout = context.getLayoutInflater().inflate(R.layout.rating, null);
		final RatingBar ratingBar = (RatingBar) layout.findViewById(R.id.rating_bar);
		ratingBar.setRating((float) entry.getRating());
		
		AlertDialog.Builder builder = new AlertDialog.Builder(context);
		builder.setTitle(context.getResources().getString(R.string.rating_title, entry.getTitle()))
			.setView(layout)
			.setPositiveButton(R.string.common_ok, new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int id) {
					int rating = (int) ratingBar.getRating();
					setRating(entry, rating, onRatingChange);
				}
			})
			.setNegativeButton(R.string.common_cancel, null);
		
		AlertDialog dialog = builder.create();
		dialog.show();
	}

	protected void setRating(Entry entry, int rating) {
		setRating(entry, rating, null);
	}
	protected void setRating(final Entry entry, final int rating, final OnRatingChange onRatingChange) {
		final int oldRating = entry.getRating();
		entry.setRating(rating);

		if(onRatingChange != null) {
			onRatingChange.ratingChange(rating);
		}
		
		new SilentBackgroundTask<Void>(context) {
			@Override
			protected Void doInBackground() throws Throwable {
				MusicService musicService = MusicServiceFactory.getMusicService(context);
				musicService.setRating(entry, rating, context, null);

				new EntryInstanceUpdater(entry) {
					@Override
					public void update(Entry found) {
						found.setRating(rating);
					}
				}.execute();
				return null;
			}

			@Override
			protected void done(Void result) {
				Util.toast(context, context.getResources().getString(rating > 0 ? R.string.rating_set_rating : R.string.rating_remove_rating, entry.getTitle()));
			}

			@Override
			protected void error(Throwable error) {
				entry.setRating(oldRating);
				if(onRatingChange != null) {
					onRatingChange.ratingChange(oldRating);
				}
				
				String msg;
				if (error instanceof OfflineException || error instanceof ServerTooOldException) {
					msg = getErrorMessage(error);
				} else {
					msg = context.getResources().getString(rating > 0 ? R.string.rating_set_rating_failed : R.string.rating_remove_rating_failed, entry.getTitle()) + " " + getErrorMessage(error);
				}

				Util.toast(context, msg, false);
			}
		}.execute();
	}
	
	protected abstract class EntryInstanceUpdater {
		private Entry entry;
		
		public EntryInstanceUpdater(Entry entry) {
			this.entry = entry;
		}
		
		public abstract void update(Entry found);
		
		public void execute() {
			DownloadService downloadService = getDownloadService();
			if(downloadService != null && !entry.isDirectory()) {
				boolean serializeChanges = false;
				List<DownloadFile> downloadFiles = downloadService.getDownloads();
				for(DownloadFile file: downloadFiles) {
					Entry check = file.getSong();
					if(entry.getId().equals(check.getId())) {
						update(entry);
						serializeChanges = true;
					}
				}
				
				if(serializeChanges) {
					downloadService.serializeQueue();
				}
			}
			
			Entry find = UpdateView.findEntry(entry);
			if(find != null) {
				update(find);
			}
		}
	}

	public abstract class OnRatingChange {
		abstract void ratingChange(int rating);
	}
	public abstract class OnStarChange {
		abstract void starChange(boolean starred);
	}

	public abstract class RecursiveLoader extends LoadingTask<Boolean> {
		protected MusicService musicService;
		protected static final int MAX_SONGS = 500;
		protected boolean playNowOverride = false;
		protected List<Entry> songs;

		public RecursiveLoader(Activity context) {
			super(context);
		}

		protected void getSongsRecursively(MusicDirectory parent, List<Entry> songs) throws Exception {
			if (songs.size() > MAX_SONGS) {
				return;
			}

			for (Entry song : parent.getChildren(false, true)) {
				if (!song.isVideo() && song.getRating() != 1) {
					songs.add(song);
				}
			}
			for (Entry dir : parent.getChildren(true, false)) {
				if(dir.getRating() == 1) {
					continue;
				}

				MusicDirectory musicDirectory;
				if(Util.isTagBrowsing(context) && !Util.isOffline(context)) {
					musicDirectory = musicService.getAlbum(dir.getId(), dir.getTitle(), false, context, this);
				} else {
					musicDirectory = musicService.getMusicDirectory(dir.getId(), dir.getTitle(), false, context, this);
				}
				getSongsRecursively(musicDirectory, songs);
			}
		}

		@Override
		protected void done(Boolean result) {
			warnIfStorageUnavailable();

			if(playNowOverride) {
				playNow(songs);
				return;
			}

			if(result) {
				Util.startActivityWithoutTransition(context, DownloadActivity.class);
			}
		}
	}
}

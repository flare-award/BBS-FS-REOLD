package mchorse.bbs_mod;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.core.ValueLinkList;
import mchorse.bbs_mod.settings.values.core.ValueRecentData;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.ui.ValueColors;
import mchorse.bbs_mod.settings.values.ui.ValueEditorLayout;
import mchorse.bbs_mod.settings.values.ui.ValueIKDebug;
import mchorse.bbs_mod.settings.values.ui.ValueKeyframeStyle;
import mchorse.bbs_mod.settings.values.ui.ValueLanguage;
import mchorse.bbs_mod.settings.values.ui.ValueMotionPath;
import mchorse.bbs_mod.settings.values.ui.ValueOnionSkin;
import mchorse.bbs_mod.settings.values.ui.ValuePhysicsDebug;
import mchorse.bbs_mod.settings.values.ui.ValueOrder;
import mchorse.bbs_mod.settings.values.ui.ValueStringKeys;
import mchorse.bbs_mod.settings.values.ui.ValueTrackStyles;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.colors.Oklab;
import mchorse.bbs_mod.utils.interps.IInterp;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.KeyframeStyle;

public class BBSSettings {

	public static final String DEFAULT_FFMPEG_ARGUMENTS = "-f rawvideo -pix_fmt bgr24 -s %WIDTH%x%HEIGHT% -r %FPS% -i - -vf %FILTERS% -c:v libx264 -preset ultrafast -tune zerolatency -qp 18 -pix_fmt yuv420p %NAME%.mp4";
	public static final String DEFAULT_AUDIO_FFMPEG_ARGUMENTS = "-f rawvideo -pix_fmt bgr24 -s %WIDTH%x%HEIGHT% -r %FPS% -i - -i %AUDIO_TRACK% -vf %FILTERS% -c:v libx264 -preset ultrafast -tune zerolatency -qp 18 -pix_fmt yuv420p -c:a aac -b:a 128k -shortest %NAME%.mp4";
	public static final String DEFAULT_MUX_FFMPEG_ARGUMENTS = "-y -i %VIDEO% -i %AUDIO_TRACK% -map 0:v:0 -map 1:a:0 -c:v copy -c:a aac -b:a 192k -shortest %NAME%.mp4";

	/* Shared HSL wheel order: the UI, clip channels and shader use the same eight colour bands. */
	public static final String[] HSL_COLOR_IDS = {
		"red", "orange", "yellow", "green", "cyan", "blue", "purple", "magenta"
	};
	public static final int HSL_COLOR_COUNT = HSL_COLOR_IDS.length;

	public static ValueColors favoriteColors;
	public static ValueColors recentColors;
	public static ValueStringKeys disabledSheets;
	public static ValueTrackStyles trackStyles;
	public static ValueStringKeys disabledMorphFormCategories;
	public static ValueLanguage language;
	public static ValueInt primaryColor;
	public static ValueBoolean primaryColorGradient;
	public static ValueInt primaryColorEnd;
	public static ValueInt primaryColorGradientDirection;
	public static ValueInt stencilHighlightColor;
	public static ValueBoolean enableTrackpadIncrements;
	public static ValueBoolean enableTrackpadScrolling;
	public static ValueFloat userIntefaceScale;
	public static ValueBoolean pixelArtSmoothing;
	public static ValueInt taskbarSide;
	public static ValueInt tabStripSide;
	public static ValueBoolean openDataList;
	public static ValueFloat fov;
	public static ValueBoolean colorPickerHsvTab;
	public static ValueBoolean forceQwerty;
	public static ValueBoolean freezeModels;
	public static ValueBoolean listModelPreview;
	public static ValueBoolean morphingFocusSearch;
	public static ValueInt formCellSize;
	public static ValueInt textureCellSize;
	public static ValueString textureSort;
	public static ValueLinkList texturePins;
	public static ValueRecentData recentData;
	public static ValueFloat axesScale;
	public static ValueFloat axesThickness;
	public static ValueBoolean gizmoKeepScreenSize;
	public static ValueFloat gizmoPlaneSize;
	public static ValueInt rotate3dSphereMode;
	public static ValueBoolean hideInactiveHandles;
	/* The gizmo always carries every one of its elements; these say which of them
	 * reach the screen and the cursor. See mchorse.bbs_mod.ui.utils.Gizmo.Element. */
	public static ValueBoolean gizmoShowTranslate;
	public static ValueBoolean gizmoShowScale;
	public static ValueBoolean gizmoShowRotate;
	public static ValueBoolean gizmoShowViewRotate;
	public static ValueBoolean gizmoShowSphere;
	public static ValueFloat snapTranslate;
	public static ValueFloat snapRotate;
	public static ValueFloat snapScale;
	public static ValueInt gizmoHoverTolerance;
	public static ValueFloat gizmoOpacity;
	public static ValueBoolean uniformScale;
	public static ValueBoolean clickSound;
	public static ValueBoolean gizmos;
	public static ValueInt transformSpace;
	public static ValueBoolean poseMirrorEdit;
	public static ValueBoolean poseAlternateInvert;
	public static ValueBoolean poseShowDisabledBones;
	/* When on, the pose editor's "full fix" control is a 0-1 slider instead of a toggle. */
	public static ValueBoolean fullFixSlider;
	public static ValueOrder translateHotkeyOrder;
	public static ValueOrder scaleHotkeyOrder;
	public static ValueOrder rotateHotkeyOrder;
	public static ValueFloat trackballSensitivity;

	public static ValueBoolean enableCursorRendering;
	public static ValueBoolean enableMouseButtonRendering;
	public static ValueBoolean enableKeystrokeRendering;
	public static ValueInt keystrokeOffset;
	public static ValueInt keystrokeMode;

	/* First run: the welcome screen shows once, and each tour chapter is ticked off by its id */
	public static ValueBoolean onboardingWelcomeSeen;
	public static ValueStringKeys onboardingToursDone;

	public static ValueLink backgroundImage;
	public static ValueInt backgroundColor;

	public static ValueBoolean chromaSkyEnabled;
	public static ValueInt chromaSkyColor;
	public static ValueBoolean chromaSkyTerrain;
	public static ValueFloat chromaSkyBillboard;

	public static ValueInt scrollbarWidth;
	public static ValueFloat scrollingSensitivity;
	public static ValueFloat scrollingSensitivityHorizontal;
	public static ValueBoolean scrollingSmoothness;
	public static ValueFloat scrollingSmoothnessIntensity;
	public static ValueBoolean scrollingDisableSmoothnessInEditors;

	public static ValueBoolean multiskinMultiThreaded;

	public static ValueString videoEncoderPath;
	public static ValueBoolean videoEncoderLog;
	public static ValueBoolean worldExportResizeWindow;
	public static ValueInt videoWidth;
	public static ValueInt videoHeight;
	public static ValueInt videoFrameRate;
	public static ValueBoolean videoLimitFrameRate;
	public static ValueString videoExportPath;
	public static ValueString videoExportFilenameFormat;
	public static ValueBoolean videoExportAudio;
	public static ValueBoolean videoExportMinecraftSounds;
	public static ValueBoolean videoMuteAudioWhileRender;
	public static ValueInt videoMotionBlur;
	public static ValueInt videoHeldFrames;
	public static ValueFloat videoDelay;
	public static ValueBoolean videoOpenFolderAfterExport;
	public static ValueBoolean videoPlaySoundAfterExport;
	public static ValueString videoArguments;
	public static ValueString videoArgumentsAudio;
	public static ValueString videoArgumentsMux;

	public static ValueFloat editorCameraSpeed;
	public static ValueFloat editorCameraAngleSpeed;
	public static ValueInt duration;
	public static ValueBoolean editorLoop;
	public static ValueBoolean autoKeyframe;
	public static ValueBoolean anchorKeepTransform;
	public static ValueInt editorJump;
	public static ValueInt editorGuidesColor;
	public static ValueBoolean editorRuleOfThirds;
	public static ValueBoolean editorCenterLines;
	public static ValueBoolean editorCrosshair;
	public static ValueBoolean editorSeconds;
	public static ValueBoolean editorTimelineGrid;
	public static ValueInt editorPeriodicSave;
	public static ValueBoolean editorHorizontalFlight;
	public static ValueBoolean editorFlightFreeLook;
	public static ValueBoolean editorOrbitMovementRequiresFlight;
	public static ValueBoolean editorOrbitCenterMarker;
	public static ValueBoolean editorOrbitGizmo;
	public static ValueFloat editorOrbitGizmoScale;
	public static ValueBoolean editorOrbitAxisOrtho;
	public static ValueMotionPath editorMotionPath;
	public static ValueBoolean editorOrbitTeleportOnSwitch;
	public static ValueFloat editorCameraSmoothness;
	public static ValueInt editorCameraMode;
	public static ValueBoolean editorPlayerFollowsCamera;
	public static ValueEditorLayout editorLayoutSettings;
	public static ValueOnionSkin editorOnionSkin;
	public static ValueIKDebug ikDebug;
	public static ValuePhysicsDebug physicsDebug;
	public static ValueBoolean profilerOverlay;
	/** Emergency switch for the per-frame pose caches; invisible, on by default. */
	public static ValueBoolean framePoseCache;
	/** Skip rendering replays whose surroundings are entirely off screen. */
	public static ValueBoolean frustumCulling;
	public static ValueBoolean editorSnapToMarkers;
	/** Snapping to the film's own markers &mdash; unlike {@link #editorSnapToMarkers}, which is the ruler's notches. */
	public static ValueBoolean editorSnapToFilmMarkers;
	public static ValueBoolean editorClipPreview;
	public static ValueBoolean editorRewind;
	public static ValueBoolean editorStopPlaybackOnScrub;
	public static ValueBoolean editorSnapToTicks;
	public static ValueBoolean editorRestartOnSeek;
	public static ValueBoolean editorHorizontalClipEditor;
	public static ValueBoolean editorMinutesBackup;
	public static ValueBoolean editorResizablePanels;
	public static ValueInt editorTrackWidth;
	public static ValueKeyframeStyle keyframeDefaultStyle;
	public static ValueString keyframeDefaultInterpolation;
	public static ValueBoolean keyframePreview;
	public static ValueInt editorPreviewSizeMode;
	public static ValueInt editorPreviewCustomWidth;
	public static ValueInt editorPreviewCustomHeight;
	public static ValueFloat editorPreviewResolutionScale;
	public static ValueBoolean editorClipAutoName;
	public static ValueBoolean editorPreviewIconsAutoHide;
	public static ValueBoolean editorPreviewSelectionHud;
	public static ValueBoolean editorKeepFrameOnExit;

	public static ValueFloat recordingCountdown;
	public static ValueBoolean recordingSwipeDamage;
	public static ValueBoolean recordingOverlays;
	public static ValueInt recordingPoseOverlays;
	public static ValueInt recordingTransformOverlays;
	public static ValueBoolean recordingCameraPreview;
	public static ValueBoolean recordingTeleport;

	public static ValueBoolean renderAllModelBlocks;
	public static ValueBoolean clickModelBlocks;

	public static ValueString entitySelectorsPropertyWhitelist;

	public static ValueBoolean damageControl;

	public static ValueInt secondaryColor;
	public static ValueFloat overlayBackgroundOpacity;

	/* Custom interface background: 0 keeps the secondary colour's own ladder, 1 recolors
	 * it with interfaceBackgroundColor, 2 flows it from that color into
	 * interfaceBackgroundColorEnd across the screen in the chosen direction. */
	public static ValueInt backgroundColorMode;
	public static ValueInt interfaceBackgroundColor;
	public static ValueInt interfaceBackgroundColorEnd;
	public static ValueInt backgroundGradientDirection;
	public static ValueBoolean interfaceShadows;
	public static ValueBoolean interfaceHighlights;
	public static ValueBoolean interfaceGlow;
	public static ValueBoolean interfaceBlur;
	public static ValueInt interfaceBlurRadius;
	public static ValueInt modelEditorTransparency;

	/* Color grading filters baked into the film preview and video export (all neutral by default). */
	public static ValueFloat filmFilterBrightness;
	public static ValueFloat filmFilterContrast;
	public static ValueFloat filmFilterSaturation;
	public static ValueFloat filmFilterHue;
	public static ValueFloat filmFilterTemperature;
	public static ValueFloat filmFilterGamma;
	public static ValueFloat filmFilterSharpness;
	public static ValueFloat filmFilterVignette;
	public static ValueFloat filmFilterSepia;
	public static ValueFloat filmFilterGrain;
	public static ValueFloat filmFilterAberration;
	public static ValueFloat filmFilterInvert;
	public static ValueFloat filmFilterPosterize;
	public static ValueFloat filmFilterPixelate;
	public static ValueFloat filmFilterDistortion;
	public static ValueFloat filmFilterBloom;
	public static ValueFloat filmFilterRadial;
	public static ValueFloat filmFilterVhs;
	public static ValueFloat filmFilterFlip;
	public static ValueFloat filmFilterFisheye;
	public static ValueFloat[] filmFilterHslHue;
	public static ValueFloat[] filmFilterHslSaturation;
	public static ValueFloat[] filmFilterHslLightness;

	/* A photo laid over the film preview and export - PNG transparency respected.
	 * Position is in NDC-like units (0 centered, positive X right, positive Y down),
	 * scale is the photo's height relative to the frame's, stretches are multipliers.
	 * The single-photo values are legacy: they migrate into the layer list below. */
	public static ValueString filmPhotoTexture;
	public static ValueFloat filmPhotoOpacity;
	public static ValueFloat filmPhotoX;
	public static ValueFloat filmPhotoY;
	public static ValueFloat filmPhotoScale;
	public static ValueFloat filmPhotoStretchX;
	public static ValueFloat filmPhotoStretchY;

	/* Serialized list of photo overlay layers (see the client's PhotoLayer class). */
	public static ValueString filmPhotoLayers;

	public static ValueBoolean shaderCurvesEnabled;
	public static ValueBoolean translucencyQueue;

	public static ValueBoolean audioWaveformVisibleInPreview;
	public static ValueBoolean audioWaveformVisibleInKeyframes;
	public static ValueInt audioWaveformDensity;
	public static ValueFloat audioWaveformWidth;
	public static ValueInt audioWaveformHeight;
	public static ValueBoolean audioWaveformFilename;
	public static ValueBoolean audioWaveformTime;
	public static ValueBoolean audioWaveformPreviewCombined;

	public static ValueString cdnUrl;
	public static ValueString cdnToken;

	private static final int DEFAULT_PRIMARY_COLOR = 0xff3242;
	private static final int DEFAULT_PRIMARY_COLOR_END = 0xff8a3c;
	private static final float DEFAULT_OVERLAY_BACKGROUND_OPACITY = 0.5F;
	private static final int DEFAULT_MODEL_EDITOR_TRANSPARENCY = 25;
	private static final int MAX_MODEL_EDITOR_TRANSPARENCY = 100;
	public static final float MIN_FILM_GAMMA = 0.25F;
	public static final float MAX_FILM_GAMMA = 4F;
	public static final float MIN_FILM_PHOTO_SCALE = 0.05F;
	public static final float MAX_FILM_PHOTO_SCALE = 3F;
	public static final float MIN_FILM_PHOTO_STRETCH = 0.1F;
	public static final float MAX_FILM_PHOTO_STRETCH = 5F;
	public static final float MAX_FILM_PHOTO_OFFSET = 2F;
	public static final float MAX_FILM_POSTERIZE = 32F;
	public static final float MAX_FILM_PIXELATE = 64F;

	/* Directions the primary color gradient can flow in */
	public static final int GRADIENT_HORIZONTAL = 0;
	public static final int GRADIENT_VERTICAL = 1;
	public static final int GRADIENT_DIAGONAL = 2;

	/* Background color modes */
	public static final int BACKGROUND_DEFAULT = 0;
	public static final int BACKGROUND_SOLID = 1;
	public static final int BACKGROUND_GRADIENT = 2;
	private static final int DEFAULT_BACKGROUND_COLOR = 0x1d1d1d;
	private static final int DEFAULT_BACKGROUND_COLOR_END = 0x101a26;

	/**
	 * Tonal map of the interface's surfaces, four levels deep: deep sits under
	 * the content (fields, timeline wells), chrome frames everything, base is
	 * the working area, raised floats above it (panels, popups, buttons), and
	 * the divider line sits a step above all of them.
	 *
	 * All five fall out of a single colour — the secondary colour the user
	 * picks — by stepping its lightness in Oklab and carrying its tint through
	 * untouched. Oklab is what makes one colour enough: a step there reads as
	 * the same step in depth whatever the tint, so the ladder stays as legible
	 * in a blue interface as in a grey one, and picking a background is one
	 * decision rather than a pile of them.
	 *
	 * How far apart the levels sit came off a screenshot of Essential's
	 * interface, whose dominant grey and the greys layered over it stand one
	 * step apart (#131313, #181818, #1d1d1d, #222222, divider #2a2a2a) — the
	 * ladder below reproduces that spacing exactly, with one further rung under
	 * the darkest for the strips that sit below all of it.
	 * {@link #DEFAULT_SECONDARY_COLOR} itself rides that ladder a hair under
	 * #1d1d1d, tinted faintly blue — a tint every rung carries through. The step
	 * is deliberately small: depth should be felt rather than announced, and a
	 * dark interface that stays dark is easier to sit in front of for hours.
	 */
	private static final int DEFAULT_SECONDARY_COLOR = 0x171b22;
	private static final float SURFACE_STEP = 0.022F;
	private static final float DIVIDER_STEP = 0.054F;

	private static final int SURFACE_SUNKEN = 0;
	private static final int SURFACE_DEEP = 1;
	private static final int SURFACE_CHROME = 2;
	private static final int SURFACE_BASE = 3;
	private static final int SURFACE_RAISED = 4;
	private static final int SURFACE_DIVIDER = 5;

	private static final float[] SURFACE_OFFSETS = {-SURFACE_STEP * 3F, -SURFACE_STEP * 2F, -SURFACE_STEP, 0F, SURFACE_STEP, DIVIDER_STEP};

	/**
	 * The lightness past which the surfaces are bright enough that white icons
	 * and text would vanish into them. It is read off the secondary colour
	 * rather than chosen: pick a light one and the interface turns light by
	 * itself, which is why there is no theme switch any more.
	 */
	private static final float LIGHT_SURFACE_LIGHTNESS = 0.5F;

	private static final Oklab SURFACE_OKLAB = new Oklab();
	private static final int[] SURFACES = new int[SURFACE_OFFSETS.length];
	/** The gradient's far end: a second ladder built from interfaceBackgroundColorEnd. */
	private static final int[] SURFACES_GRADIENT = new int[SURFACE_OFFSETS.length];

	/** The colours {@link #SURFACES} and {@link #SURFACES_GRADIENT} were derived from; -1/0 are none, so the first read builds. */
	private static int surfaceSource = -1;
	private static int gradientSource = 0;
	private static boolean lightSurfaces;

	public static int primaryColor()
	{
		return primaryColor(Colors.A50);
	}

	public static int primaryColor(int alpha)
	{
		return withAlpha(primaryColor.get(), alpha);
	}

	/**
	 * Whether the accent flows from {@link #primaryColor} into {@link #primaryColorEnd}
	 * as a gradient on buttons instead of staying a single flat color.
	 */
	public static boolean isPrimaryGradient()
	{
		return primaryColorGradient != null && primaryColorGradient.get();
	}

	public static int primaryColorEnd()
	{
		return primaryColorEnd == null ? DEFAULT_PRIMARY_COLOR_END : primaryColorEnd.get();
	}

	/**
	 * Which way the accent gradient flows: {@link #GRADIENT_HORIZONTAL},
	 * {@link #GRADIENT_VERTICAL} or {@link #GRADIENT_DIAGONAL}.
	 */
	public static int primaryGradientDirection()
	{
		return primaryColorGradientDirection == null ? GRADIENT_HORIZONTAL : primaryColorGradientDirection.get();
	}

	private static int withAlpha(int color, int alpha)
	{
		return (color & Colors.RGB) | alpha;
	}

	/**
	 * Rebuild the ladder, but only when the secondary colour actually moved —
	 * surfaces are asked for many times a frame, and the conversion is a
	 * handful of cube roots.
	 */
	private static void buildSurfaces()
	{
		int mode = backgroundColorMode();
		int color = mode != BACKGROUND_DEFAULT && interfaceBackgroundColor != null
			? interfaceBackgroundColor.get() & Colors.RGB
			: secondaryColor == null ? DEFAULT_SECONDARY_COLOR : secondaryColor.get() & Colors.RGB;
		int gradientColor = mode != BACKGROUND_GRADIENT || interfaceBackgroundColorEnd == null
			? 0 : interfaceBackgroundColorEnd.get() & Colors.RGB;

		if (color == surfaceSource && gradientColor == gradientSource)
		{
			return;
		}

		SURFACE_OKLAB.set(color);

		for (int i = 0; i < SURFACES.length; i++)
		{
			SURFACES[i] = SURFACE_OKLAB.toRGB(SURFACE_OKLAB.l + SURFACE_OFFSETS[i]);
		}

		if (mode == BACKGROUND_GRADIENT)
		{
			SURFACE_OKLAB.set(gradientColor);

			for (int i = 0; i < SURFACES_GRADIENT.length; i++)
			{
				SURFACES_GRADIENT[i] = SURFACE_OKLAB.toRGB(SURFACE_OKLAB.l + SURFACE_OFFSETS[i]);
			}
		}

		lightSurfaces = SURFACE_OKLAB.l > LIGHT_SURFACE_LIGHTNESS;
		surfaceSource = color;
		gradientSource = gradientColor;
	}

	private static int surface(int level)
	{
		buildSurfaces();

		return applySurfaceTransparency(SURFACES[level]);
	}

	/**
	 * Whether the interface currently sits on light surfaces, in which case
	 * white icons and text have to be flipped to dark to stay readable.
	 */
	public static boolean lightSurfaces()
	{
		buildSurfaces();

		return lightSurfaces;
	}

	public static int chromeSurface()
	{
		return surface(SURFACE_CHROME);
	}

	public static int baseSurface()
	{
		return surface(SURFACE_BASE);
	}

	public static int raisedSurface()
	{
		return surface(SURFACE_RAISED);
	}

	public static int deepSurface()
	{
		return surface(SURFACE_DEEP);
	}

	/**
	 * One rung below {@link #deepSurface()}: the floor of the ladder, for the
	 * strips that have to sit under everything the interface layers on top —
	 * the timeline ruler and the field outside the film, which is not a surface
	 * anything can be put on.
	 */
	public static int sunkenSurface()
	{
		return surface(SURFACE_SUNKEN);
	}

	public static int dividerColor()
	{
		return surface(SURFACE_DIVIDER);
	}

	public static int color(int color, int alpha)
	{
		return withAlpha(color, alpha);
	}

	public static int accentOverlay(int alpha)
	{
		return primaryColor(alpha);
	}

	/**
	 * Render-scoped: the film editor sets this so its inputs stay light on its dark panels.
	 */
	public static boolean lightInputs = false;

	public static int inputSurface()
	{
		return lightInputs ? raisedSurface() : deepSurface();
	}

	/**
	 * Render-scoped, like {@link #lightInputs}: the form editor sets this for the
	 * duration of its own rendering so the surfaces of its panels let the world
	 * show through. Zero keeps every surface exactly as solid as it always was.
	 */
	public static float surfaceTransparency = 0F;

	private static int applySurfaceTransparency(int color)
	{
		if (surfaceTransparency <= 0F)
		{
			return color;
		}

		float factor = 1F - MathUtils.clamp(surfaceTransparency, 0F, 1F);
		int alpha = Math.round(((color >> 24) & 0xff) * factor);

		return withAlpha(color, MathUtils.clamp(alpha, 0, 255) << 24);
	}

	/**
	 * The form editor's transparency percentage as a 0..1 factor that
	 * {@link #surfaceTransparency} understands. Zero keeps the panels solid,
	 * a hundred percent makes them fully see-through. The config key predates
	 * the setting covering every form type, hence the name.
	 */
	public static float modelEditorTransparency()
	{
		int percent = modelEditorTransparency == null ? DEFAULT_MODEL_EDITOR_TRANSPARENCY : modelEditorTransparency.get();

		return MathUtils.clamp(percent, 0, MAX_MODEL_EDITOR_TRANSPARENCY) / (float) MAX_MODEL_EDITOR_TRANSPARENCY;
	}

	public static int backgroundColorMode()
	{
		return backgroundColorMode == null ? BACKGROUND_DEFAULT : MathUtils.clamp(backgroundColorMode.get(), BACKGROUND_DEFAULT, BACKGROUND_GRADIENT);
	}

	public static boolean isBackgroundGradient()
	{
		return backgroundColorMode() == BACKGROUND_GRADIENT;
	}

	public static int backgroundGradientDirection()
	{
		return backgroundGradientDirection == null ? GRADIENT_HORIZONTAL : backgroundGradientDirection.get();
	}

	/**
	 * The gradient's far-end twin of a surface fill color, or 0 when the given
	 * color isn't one of the current background surfaces (or the background
	 * isn't in gradient mode). The UI batcher asks this to know which flat
	 * fills should flow across the screen instead.
	 */
	public static int backgroundGradientEnd(int surfaceColor)
	{
		if (!isBackgroundGradient())
		{
			return 0;
		}

		buildSurfaces();

		for (int i = 0; i < SURFACES.length; i++)
		{
			if (surfaceColor == SURFACES[i])
			{
				return SURFACES_GRADIENT[i];
			}
		}

		return 0;
	}

	/** Show only the background color settings the current mode makes use of. */
	public static void updateBackgroundSettingsVisibility()
	{
		int mode = backgroundColorMode();

		if (interfaceBackgroundColor != null)
		{
			interfaceBackgroundColor.visible(mode != BACKGROUND_DEFAULT);
			interfaceBackgroundColorEnd.visible(mode == BACKGROUND_GRADIENT);
			backgroundGradientDirection.visible(mode == BACKGROUND_GRADIENT);
		}
	}

	public static int panelShadowOpaqueColor()
	{
		return Colors.A25 | primaryColor.get();
	}

	public static int panelShadowTransparentColor()
	{
		return Colors.setA(primaryColor.get(), 0F);
	}

	/**
	 * Dimming behind an overlay panel. Zero opacity leaves whatever is behind
	 * the panel fully visible.
	 */
	public static int overlayBackground()
	{
		float opacity = overlayBackgroundOpacity == null ? DEFAULT_OVERLAY_BACKGROUND_OPACITY : overlayBackgroundOpacity.get();

		return Colors.a(MathUtils.clamp(opacity, 0F, 1F));
	}

	/**
	 * Whether the interface draws its soft glows at all. Every one of them goes
	 * through {@code Batcher2D.dropShadow}, so this is read there rather than
	 * at each caller — the toggle covers panels, context menus, tooltips,
	 * notifications and anything added later without them knowing about it.
	 */
	public static boolean hasInterfaceGlow()
	{
		return interfaceGlow == null || interfaceGlow.get();
	}

	public static int getDefaultDuration()
	{
		return duration == null ? 100 : duration.get();
	}

	/** Shared strength for smooth scrolling and timeline zoom; zero selects immediate movement. */
	public static float getScrollSmoothingIntensity()
	{
		return scrollingSmoothness.get() ? scrollingSmoothnessIntensity.get() : 0F;
	}

	public static float getFov()
	{
		return BBSSettings.fov == null ? MathUtils.toRad(70) : MathUtils.toRad(BBSSettings.fov.get());
	}

	/**
	 * How much a world-space overlay has to grow with distance to keep the same size on
	 * screen. Markers and paths always want this - what they mark is a point, and a point
	 * that shrinks into nothing marks nothing.
	 */
	public static float getScreenSizeScale(float distance)
	{
		return getScreenSizeScale(distance, getFov());
	}

	public static float getScreenSizeScale(float distance, float fov)
	{
		float tanFov = (float) Math.tan(fov / 2.0);
		// 0.4663F is roughly tan(50 degrees / 2)
		float scale = (distance / 5F) * (tanFov / 0.4663F);

		return Math.max(scale, 0.0001F);
	}

	public static boolean isHorizontalClipEditorEffective()
	{
		return editorHorizontalClipEditor.get();
	}

	/**
	 * A fresh copy of the style newly created keyframes are drawn with. It is a copy because the
	 * keyframe owns what it gets: editing one keyframe's style must not reach back into the setting
	 * every other keyframe was born from.
	 */
	public static KeyframeStyle getDefaultKeyframeStyle()
	{
		return keyframeDefaultStyle == null ? new KeyframeStyle() : keyframeDefaultStyle.get().copy();
	}

	/**
	 * The interpolation given to a hand-created keyframe when it has no neighbour to inherit
	 * from (see {@code IUIKeyframeGraph#addKeyframeManually}) - i.e. the replacement for the
	 * hardcoded linear that used to apply in that "empty spot" case. Keyframes that do inherit
	 * from a neighbour keep the neighbour's interpolation, and recorded/baked keyframes never
	 * consult this. Falls back to linear before settings are registered or on an unknown key.
	 */
	public static IInterp getDefaultKeyframeInterpolation()
	{
		if (keyframeDefaultInterpolation == null)
		{
			return Interpolations.LINEAR;
		}

		IInterp interp = Interpolations.MAP.get(keyframeDefaultInterpolation.get());

		return interp == null ? Interpolations.LINEAR : interp;
	}

	/**
	 * Bring a settings file written by an older version onto the current category
	 * layout. Every rule moves a value out of the category it used to live in and
	 * into the one it lives in now; a value that already exists in the new
	 * category wins, so migrating never overwrites a newer setting. The file is
	 * rewritten by {@link mchorse.bbs_mod.settings.SettingsManager} right after,
	 * which is what drops the emptied out legacy categories.
	 */
	public static boolean migrateLegacySettings(MapType root)
	{
		boolean migrated = false;

		/* Colors and timeline looks moved out of the general appearance category */
		migrated |= migrateLegacyCategory(root, "appearance", "personalization", "primary_color", "track_width", "keyframe_default_shape");

		/* The camera editor category got split into the parts it was made of */
		migrated |= migrateLegacyCategory(root, "editor", "camera",
			"speed", "angle_speed", "horizontal_flight", "camera_smoothness", "player_follows_camera",
			"orbit_movement_requires_flight", "orbit_center_marker", "orbit_gizmo", "orbit_gizmo_scale",
			"orbit_axis_ortho", "orbit_teleport_on_switch", "camera_mode");
		migrated |= migrateLegacyCategory(root, "editor", "viewport",
			"guides_color", "rule_of_thirds", "center_lines", "crosshair", "preview_size_mode",
			"preview_custom_width", "preview_custom_height", "preview_resolution_scale", "clip_preview",
			"onion_skin", "motion_path", "ik_debug", "physics_debug");
		migrated |= migrateLegacyCategory(root, "editor", "timeline",
			"duration", "jump", "loop", "seconds", "timeline_grid", "keyframe_default_interpolation",
			"snap_to_markers", "rewind", "horizontal_clip_editor");
		migrated |= migrateLegacyCategory(root, "editor", "workspace",
			"layout", "resizable_panels", "periodic_save", "minutes_backup", "keep_frame_on_exit");
		/* Debug overlays briefly had a category of their own, which had nothing to
		 * show since they are edited from the IK and physics panels */
		migrated |= migrateLegacyCategory(root, "debug", "viewport", "ik_debug", "physics_debug");

		/* The panel glow became a glow toggle for the whole interface */
		migrated |= migrateLegacyValue(root, "personalization", "overlay_gradient_border", "personalization", "interface_glow");

		/* Timeline looks and clip naming joined the categories they belong to */
		migrated |= migrateLegacyCategory(root, "personalization", "timeline", "track_width", "keyframe_default_shape");
		migrated |= migrateLegacyCategory(root, "appearance", "workspace", "clip_auto_name");

		/* The performance knobs gathered into a category of their own */
		migrated |= migrateLegacyCategory(root, "appearance", "performance", "list_model_preview", "freeze_models");
		migrated |= migrateLegacyCategory(root, "viewport", "performance", "profiler_overlay", "frame_pose_cache");
		migrated |= migrateLegacyCategory(root, "misc", "performance", "translucency_queue", "multiskin_multithreaded");

		/* Video capture was briefly split three ways, which turned out to be worse
		 * than the one long page it came from */
		migrated |= migrateLegacyCategory(root, "export", "video",
			"export_path", "filename_format", "open_folder_after_export", "play_sound_after_export",
			"world_export_resize_window", "audio", "minecraft_sounds", "mute_audio_while_render");
		migrated |= migrateLegacyCategory(root, "encoder", "video",
			"encoder_path", "log", "arguments", "arguments_audio", "arguments_mux");

		/* The gizmo lost its display modes: every element is always there, and these
		 * two toggles became part of the per-element visibility set */
		migrated |= migrateLegacyValue(root, "transformation", "axes_keep_screen_size", "transformation", "gizmo_keep_screen_size");
		migrated |= migrateLegacyValue(root, "transformation", "rotate_3d_sphere", "transformation", "gizmo_show_sphere");
		migrated |= migrateLegacyFlipped(root, "transformation", "rotate_hide_rings", "transformation", "gizmo_show_rotate");

		/* Single option features share one category now, so their ids say what they switch */
		migrated |= migrateLegacyValue(root, "dc", "enabled", "misc", "damage_control");
		migrated |= migrateLegacyValue(root, "shader_curves", "enabled", "misc", "shader_curves");
		migrated |= migrateLegacyValue(root, "multiskin", "multithreaded", "misc", "multiskin_multithreaded");
		migrated |= migrateLegacyValue(root, "entity_selectors", "whitelist", "misc", "entity_selectors_whitelist");
		migrated |= migrateLegacyValue(root, "recording", "pose_transform_overlays", "recording", "pose_overlays");
		migrated |= migrateLegacyValue(root, "recording", "pose_transform_overlays", "recording", "transform_overlays");

		/* Sections now keep the replay list compact. Reveal their channels once;
		 * later manual filtering must survive reloads. */
		MapType appearance = root.getMap("appearance");

		if (!appearance.getBool("replay_sections_filter_migrated"))
		{
			HashSet<String> revealed = new HashSet<>(ReplayKeyframes.CURATED_CHANNELS);
			revealed.addAll(Arrays.asList("leaning", "roll", "fall"));
			revealed.removeAll(Arrays.asList("yaw", "vX", "vY", "vZ"));
			appearance.getList("disabled_sheets").elements.removeIf(value -> value.isString() && revealed.contains(value.asString()));
			appearance.putBool("replay_sections_filter_migrated", true);
			root.put("appearance", appearance);
			migrated = true;
		}

		return migrated;
	}

	private static boolean migrateLegacyCategory(MapType root, String oldCategory, String newCategory, String... keys)
	{
		boolean migrated = false;

		for (String key : keys)
		{
			migrated |= migrateLegacyValue(root, oldCategory, key, newCategory, key);
		}

		return migrated;
	}

	/**
	 * The same, for a boolean whose meaning was turned around by the rename
	 * ("hide X" becoming "show X"), so the migrated file keeps the look the user had.
	 */
	private static boolean migrateLegacyFlipped(MapType root, String oldCategory, String oldKey, String newCategory, String newKey)
	{
		MapType oldMap = root.getMap(oldCategory);
		MapType newMap = root.getMap(newCategory);

		if (newMap.has(newKey) || !oldMap.has(oldKey))
		{
			return false;
		}

		newMap.putBool(newKey, !oldMap.getBool(oldKey));
		root.put(newCategory, newMap);

		return true;
	}

	private static boolean migrateLegacyValue(MapType root, String oldCategory, String oldKey, String newCategory, String newKey)
	{
		MapType oldMap = root.getMap(oldCategory);
		MapType newMap = root.getMap(newCategory);

		if (newMap.has(newKey) || !oldMap.has(oldKey))
		{
			return false;
		}

		newMap.put(newKey, oldMap.get(oldKey).copy());
		root.put(newCategory, newMap);

		return true;
	}

	public static void register(SettingsBuilder builder)
	{
		/* Replay sections replace the old hidden-by-default groups. */
		HashSet<String> defaultFilters = new HashSet<>(Arrays.asList("yaw", "vX", "vY", "vZ"));

		/* Interface */
		builder.category("appearance", Icons.LAYOUT);
		builder.register(language = new ValueLanguage("language"));
		enableTrackpadIncrements = builder.getBoolean("trackpad_increments", false);
		enableTrackpadScrolling = builder.getBoolean("trackpad_scrolling", false);
		userIntefaceScale = builder.getFloat("ui_scale", 2F, 0F, 4F).slider(0.25D);
		pixelArtSmoothing = builder.getBoolean("pixel_art_smoothing", true);
		taskbarSide = builder.getInt("taskbar_side", 0);
		/* Which edge of the form editor the strip of tab buttons is docked to; right is the 2.7 default. */
		tabStripSide = builder.getInt("tab_strip_side", 3);
		openDataList = builder.getBoolean("open_data_list", false);
		fov = builder.getFloat("fov", 70, 0, 180);
		colorPickerHsvTab = builder.getBoolean("hsv_color_picker", true);
		forceQwerty = builder.getBoolean("force_qwerty", false);
		morphingFocusSearch = builder.getBoolean("morphing_focus_search", false);
		formCellSize = builder.getInt("form_cell_size", 60, 40, 140).slider();
		textureCellSize = builder.getInt("texture_cell_size", 80, 40, 200).slider();
		textureSort = builder.getString("texture_sort", "name");
		texturePins = new ValueLinkList("texture_pins", List.of(Link.assets("textures/")));
		texturePins.invisible();
		builder.register(texturePins);
		recentData = new ValueRecentData("recent_data");
		recentData.invisible();
		builder.register(recentData);
		/* Kept by the browsers themselves (Ctrl+wheel, the sort menu); nothing to tune in the settings screen */
		formCellSize.invisible();
		textureCellSize.invisible();
		textureSort.invisible();
		/* Which tab the colour picker was left on, written by the picker itself when
		 * the tab is switched - a remembered position, not a setting to sit in a list.
		 * The key stays "hsv_color_picker" so an existing settings file keeps its tab. */
		colorPickerHsvTab.invisible();
		uniformScale = builder.getBoolean("uniform_scale", false);
		clickSound = builder.getBoolean("click_sound", false);
		favoriteColors = new ValueColors("favorite_colors");
		recentColors = new ValueColors("recent_colors").limit(33);
		disabledSheets = new ValueStringKeys("disabled_sheets", defaultFilters);
		builder.register(favoriteColors);
		builder.register(recentColors);
		builder.register(disabledSheets);
		builder.getBoolean("replay_sections_filter_migrated", true).invisible();
		trackStyles = new ValueTrackStyles("track_styles");
		builder.register(trackStyles);
		disabledMorphFormCategories = new ValueStringKeys("disabled_morph_form_categories");
		builder.register(disabledMorphFormCategories);

		builder.category("personalization", Icons.COLOR);
		primaryColor = builder.getInt("primary_color", DEFAULT_PRIMARY_COLOR).color();
		primaryColorGradient = builder.getBoolean("primary_color_gradient", false);
		primaryColorEnd = builder.getInt("primary_color_end", DEFAULT_PRIMARY_COLOR_END).color();
		primaryColorGradientDirection = builder.getInt("primary_color_gradient_direction", GRADIENT_HORIZONTAL, GRADIENT_HORIZONTAL, GRADIENT_DIAGONAL);
		secondaryColor = builder.getInt("secondary_color", DEFAULT_SECONDARY_COLOR).color();
		/* Custom interface background: edited here and from the settings menu alike. */
		backgroundColorMode = builder.getInt("background_color_mode", BACKGROUND_DEFAULT, BACKGROUND_DEFAULT, BACKGROUND_GRADIENT);
		interfaceBackgroundColor = builder.getInt("background_color", DEFAULT_BACKGROUND_COLOR).color();
		interfaceBackgroundColorEnd = builder.getInt("background_color_end", DEFAULT_BACKGROUND_COLOR_END).color();
		backgroundGradientDirection = builder.getInt("background_gradient_direction", GRADIENT_HORIZONTAL, GRADIENT_HORIZONTAL, GRADIENT_DIAGONAL);
		stencilHighlightColor = builder.getInt("stencil_highlight_color", 0x2EFFFFFF).colorAlpha();
		overlayBackgroundOpacity = builder.getFloat("overlay_background_opacity", DEFAULT_OVERLAY_BACKGROUND_OPACITY, 0F, 1F).slider();
		interfaceBlur = builder.getBoolean("interface_blur", true);
		interfaceBlurRadius = builder.getInt("interface_blur_radius", 12, 1, 30).slider();
		interfaceShadows = builder.getBoolean("interface_shadows", true);
		interfaceHighlights = builder.getBoolean("interface_highlights", false);
		interfaceGlow = builder.getBoolean("interface_glow", true);
		modelEditorTransparency = builder.getInt("model_editor_transparency", DEFAULT_MODEL_EDITOR_TRANSPARENCY, 0, MAX_MODEL_EDITOR_TRANSPARENCY).slider();

		/* Film preview/export filters and the photo overlay - edited from the film panel's preview bar. */
		filmFilterBrightness = builder.getFloat("film_filter_brightness", 0F, -1F, 1F);
		filmFilterBrightness.invisible();
		filmFilterContrast = builder.getFloat("film_filter_contrast", 0F, -1F, 1F);
		filmFilterContrast.invisible();
		filmFilterSaturation = builder.getFloat("film_filter_saturation", 0F, -1F, 1F);
		filmFilterSaturation.invisible();
		filmFilterHue = builder.getFloat("film_filter_hue", 0F, -180F, 180F);
		filmFilterHue.invisible();
		filmFilterTemperature = builder.getFloat("film_filter_temperature", 0F, -1F, 1F);
		filmFilterTemperature.invisible();
		filmFilterGamma = builder.getFloat("film_filter_gamma", 1F, MIN_FILM_GAMMA, MAX_FILM_GAMMA);
		filmFilterGamma.invisible();
		filmFilterSharpness = builder.getFloat("film_filter_sharpness", 0F, 0F, 1F);
		filmFilterSharpness.invisible();
		filmFilterVignette = builder.getFloat("film_filter_vignette", 0F, -1F, 1F);
		filmFilterVignette.invisible();
		filmFilterSepia = builder.getFloat("film_filter_sepia", 0F, 0F, 1F);
		filmFilterSepia.invisible();
		filmFilterGrain = builder.getFloat("film_filter_grain", 0F, 0F, 1F);
		filmFilterGrain.invisible();
		filmFilterAberration = builder.getFloat("film_filter_aberration", 0F, 0F, 1F);
		filmFilterAberration.invisible();
		filmFilterInvert = builder.getFloat("film_filter_invert", 0F, 0F, 1F);
		filmFilterInvert.invisible();
		filmFilterPosterize = builder.getFloat("film_filter_posterize", 0F, 0F, MAX_FILM_POSTERIZE);
		filmFilterPosterize.invisible();
		filmFilterPixelate = builder.getFloat("film_filter_pixelate", 0F, 0F, MAX_FILM_PIXELATE);
		filmFilterPixelate.invisible();
		filmFilterDistortion = builder.getFloat("film_filter_distortion", 0F, -1F, 1F);
		filmFilterDistortion.invisible();
		filmFilterBloom = builder.getFloat("film_filter_bloom", 0F, 0F, 1F);
		filmFilterBloom.invisible();
		filmFilterRadial = builder.getFloat("film_filter_radial", 0F, 0F, 1F);
		filmFilterRadial.invisible();
		filmFilterVhs = builder.getFloat("film_filter_vhs", 0F, 0F, 1F);
		filmFilterVhs.invisible();
		filmFilterFlip = builder.getFloat("film_filter_flip", 0F, 0F, 2F);
		filmFilterFlip.invisible();
		filmFilterFisheye = builder.getFloat("film_filter_fisheye", 0F, -1F, 1F);
		filmFilterFisheye.invisible();

		filmFilterHslHue = new ValueFloat[HSL_COLOR_COUNT];
		filmFilterHslSaturation = new ValueFloat[HSL_COLOR_COUNT];
		filmFilterHslLightness = new ValueFloat[HSL_COLOR_COUNT];

		for (int i = 0; i < HSL_COLOR_COUNT; i++)
		{
			String color = HSL_COLOR_IDS[i];

			filmFilterHslHue[i] = builder.getFloat("film_filter_hsl_" + color + "_hue", 0F, -180F, 180F);
			filmFilterHslHue[i].invisible();
			filmFilterHslSaturation[i] = builder.getFloat("film_filter_hsl_" + color + "_saturation", 0F, -1F, 1F);
			filmFilterHslSaturation[i].invisible();
			filmFilterHslLightness[i] = builder.getFloat("film_filter_hsl_" + color + "_lightness", 0F, -1F, 1F);
			filmFilterHslLightness[i].invisible();
		}

		filmPhotoTexture = builder.getString("film_photo_texture", "");
		filmPhotoTexture.invisible();
		filmPhotoOpacity = builder.getFloat("film_photo_opacity", 1F, 0F, 1F);
		filmPhotoOpacity.invisible();
		filmPhotoX = builder.getFloat("film_photo_x", 0F, -MAX_FILM_PHOTO_OFFSET, MAX_FILM_PHOTO_OFFSET);
		filmPhotoX.invisible();
		filmPhotoY = builder.getFloat("film_photo_y", 0F, -MAX_FILM_PHOTO_OFFSET, MAX_FILM_PHOTO_OFFSET);
		filmPhotoY.invisible();
		filmPhotoScale = builder.getFloat("film_photo_scale", 1F, MIN_FILM_PHOTO_SCALE, MAX_FILM_PHOTO_SCALE);
		filmPhotoScale.invisible();
		filmPhotoStretchX = builder.getFloat("film_photo_stretch_x", 1F, MIN_FILM_PHOTO_STRETCH, MAX_FILM_PHOTO_STRETCH);
		filmPhotoStretchX.invisible();
		filmPhotoStretchY = builder.getFloat("film_photo_stretch_y", 1F, MIN_FILM_PHOTO_STRETCH, MAX_FILM_PHOTO_STRETCH);
		filmPhotoStretchY.invisible();
		filmPhotoLayers = builder.getString("film_photo_layers", "");
		filmPhotoLayers.invisible();

		builder.category("scrollbars", Icons.VERTICAL);
		scrollbarWidth = builder.getInt("width", 4, 2, 10).slider();
		scrollingSensitivity = builder.getFloat("sensitivity", 3F, 0F, 10F).slider();
		scrollingSensitivityHorizontal = builder.getFloat("sensitivity_horizontal", 3F, 0F, 10F).slider();
		scrollingSmoothness = builder.getBoolean("smoothness", true);
		scrollingSmoothnessIntensity = builder.getFloat("smoothness_intensity", 0.75F, 0F, 2F).slider();
		scrollingDisableSmoothnessInEditors = builder.getBoolean("disable_smoothness_in_editors", true);

		builder.category("tutorials", Icons.HELP);
		enableCursorRendering = builder.getBoolean("cursor", false);
		enableMouseButtonRendering = builder.getBoolean("mouse_buttons", false);
		enableKeystrokeRendering = builder.getBoolean("keystrokes", false);
		keystrokeOffset = builder.getInt("keystrokes_offset", 10, 0, 20).slider();
		keystrokeMode = builder.getInt("keystrokes_position", 1);
		/* Both stay visible: the settings page draws them as the buttons that bring the
		 * welcome screen and the tours back, see UISettingsLayout */
		onboardingWelcomeSeen = builder.getBoolean("welcome_seen", false);
		onboardingToursDone = new ValueStringKeys("tours_done");
		builder.register(onboardingToursDone);

		/* Viewport */
		builder.category("transformation", Icons.SCALE);
		gizmos = builder.getBoolean("gizmos", true);
		axesScale = builder.getFloat("axes_scale", 2F, 0F, 10F).slider();
		axesThickness = builder.getFloat("axes_thickness", 0.35F, 0.25F, 3F).slider();
		gizmoPlaneSize = builder.getFloat("gizmo_plane_size", 2F, 0.25F, 3F).slider();
		gizmoKeepScreenSize = builder.getBoolean("gizmo_keep_screen_size", true);
		gizmoShowTranslate = builder.getBoolean("gizmo_show_translate", true);
		gizmoShowScale = builder.getBoolean("gizmo_show_scale", true);
		gizmoShowRotate = builder.getBoolean("gizmo_show_rotate", true);
		gizmoShowViewRotate = builder.getBoolean("gizmo_show_view_rotate", true);
		gizmoShowSphere = builder.getBoolean("gizmo_show_sphere", true);
		rotate3dSphereMode = builder.getInt("rotate_3d_sphere_mode", 0);
		hideInactiveHandles = builder.getBoolean("hide_inactive_handles", true);
		snapTranslate = builder.getFloat("snap_translate", 1F, 0.001F, 100F);
		snapRotate = builder.getFloat("snap_rotate", 5F, 0.001F, 90F);
		snapScale = builder.getFloat("snap_scale", 0.1F, 0.001F, 10F);
		gizmoHoverTolerance = builder.getInt("gizmo_hover_tolerance", 4, 0, 40).slider();
		gizmoOpacity = builder.getFloat("gizmo_opacity", 1F, 0.05F, 1F).slider();
		/* The frame every transform editor opens in, remembered from the last
		 * session; picked from the gizmo's own space picker, so it has no row here.
		 * The default is PARENT's ordinal - see TransformSpace, whose constants may
		 * only be appended because this persists the ordinal. */
		transformSpace = builder.getInt("transform_space", 3);
		transformSpace.invisible();
		poseMirrorEdit = builder.getBoolean("pose_mirror_edit", false);
		poseMirrorEdit.invisible();
		poseAlternateInvert = builder.getBoolean("pose_alternate_invert", false);
		poseAlternateInvert.invisible();
		poseShowDisabledBones = builder.getBoolean("pose_show_disabled_bones", false);
		fullFixSlider = builder.getBoolean("full_fix_slider", false);
		translateHotkeyOrder = new ValueOrder("translate_hotkey_order", "screen", "x", "y", "z");
		builder.register(translateHotkeyOrder);
		scaleHotkeyOrder = new ValueOrder("scale_hotkey_order", "all", "x", "y", "z");
		builder.register(scaleHotkeyOrder);
		rotateHotkeyOrder = new ValueOrder("rotate_hotkey_order", "view", "sphere", "x", "y", "z");
		builder.register(rotateHotkeyOrder);
		trackballSensitivity = builder.getFloat("trackball_sensitivity", 1F, 0.05F, 2F).slider();

		builder.category("camera", Icons.CAMERA);
		editorCameraSpeed = builder.getFloat("speed", 1F, 0.1F, 100F);
		editorCameraAngleSpeed = builder.getFloat("angle_speed", 1F, 0.1F, 100F);
		editorHorizontalFlight = builder.getBoolean("horizontal_flight", false);
		editorFlightFreeLook = builder.getBoolean("flight_free_look", false);
		editorCameraSmoothness = builder.getFloat("camera_smoothness", 0.1F, 0F, 0.95F).slider();
		editorPlayerFollowsCamera = builder.getBoolean("player_follows_camera", false);
		editorOrbitMovementRequiresFlight = builder.getBoolean("orbit_movement_requires_flight", true);
		editorOrbitCenterMarker = builder.getBoolean("orbit_center_marker", false);
		editorOrbitGizmo = builder.getBoolean("orbit_gizmo", true);
		editorOrbitGizmoScale = builder.getFloat("orbit_gizmo_scale", 0.75F, 0.5F, 2F).slider();
		editorOrbitAxisOrtho = builder.getBoolean("orbit_axis_ortho", true);
		editorOrbitTeleportOnSwitch = builder.getBoolean("orbit_teleport_on_switch", true);
		editorCameraMode = builder.getInt("camera_mode", 0, 0, 5);
		editorCameraMode.invisible();

		builder.category("viewport", Icons.FRUSTUM);
		editorGuidesColor = builder.getInt("guides_color", 0x7fffffff).colorAlpha();
		editorRuleOfThirds = builder.getBoolean("rule_of_thirds", false);
		editorCenterLines = builder.getBoolean("center_lines", false);
		editorCrosshair = builder.getBoolean("crosshair", false);
		editorPreviewSizeMode = builder.getInt("preview_size_mode", 2, 0, 2);
		editorPreviewCustomWidth = builder.getInt("preview_custom_width", 1280, 2, 16384);
		editorPreviewCustomHeight = builder.getInt("preview_custom_height", 720, 2, 16384);
		editorPreviewResolutionScale = builder.getFloat("preview_resolution_scale", 2F, 1F, 3F).slider();
		editorClipPreview = builder.getBoolean("clip_preview", true);
		editorPreviewIconsAutoHide = builder.getBoolean("preview_icons_auto_hide", false);
		editorPreviewSelectionHud = builder.getBoolean("preview_selection_hud", true);
		builder.register(editorOnionSkin = new ValueOnionSkin("onion_skin"));
		builder.register(editorMotionPath = new ValueMotionPath("motion_path"));
		/* Overlays drawn over the preview which are edited through the gear in the
		 * IK and physics panels - stored here, no row of their own in the settings */
		builder.register(ikDebug = new ValueIKDebug("ik_debug"));
		builder.register(physicsDebug = new ValuePhysicsDebug("physics_debug"));

		/* Everything that trades work for frames: what the editor renders at all, at what
		 * resolution and how often, what it computes in parallel, plus the counters that
		 * show where the frame goes. */
		builder.category("performance", Icons.PROCESSOR);
		listModelPreview = builder.getBoolean("list_model_preview", true);
		freezeModels = builder.getBoolean("freeze_models", false);
		translucencyQueue = builder.getBoolean("translucency_queue", false);
		multiskinMultiThreaded = builder.getBoolean("multiskin_multithreaded", true);
		frustumCulling = builder.getBoolean("frustum_culling", true);
		profilerOverlay = builder.getBoolean("profiler_overlay", false);
		framePoseCache = builder.getBoolean("frame_pose_cache", true);
		framePoseCache.invisible();

		builder.category("background", Icons.IMAGE);
		backgroundImage = builder.getRL("image", null);
		backgroundColor = builder.getInt("color", 0xbf0c0c0c).colorAlpha();

		builder.category("chroma_sky", Icons.GLOBE);
		chromaSkyEnabled = builder.getBoolean("enabled", false);
		chromaSkyColor = builder.getInt("color", Colors.A75).color();
		chromaSkyTerrain = builder.getBoolean("terrain", true);
		chromaSkyBillboard = builder.getFloat("billboard", 0F, 0F, 256F);

		/* Editor */
		builder.category("timeline", Icons.TIME);
		duration = builder.getInt("duration", 100, 1, 1000);
		editorJump = builder.getInt("jump", 5, 1, 1000);
		editorLoop = builder.getBoolean("loop", false);
		autoKeyframe = builder.getBoolean("auto_keyframe", false);
		anchorKeepTransform = builder.getBoolean("anchor_keep_transform", false);
		editorSeconds = builder.getBoolean("seconds", false);
		editorTimelineGrid = builder.getBoolean("timeline_grid", true);
		keyframeDefaultInterpolation = builder.getString("keyframe_default_interpolation", Interpolations.LINEAR.getKey());
		builder.register(keyframeDefaultStyle = new ValueKeyframeStyle("keyframe_default_style"));
		keyframePreview = builder.getBoolean("keyframe_preview", true);
		editorTrackWidth = builder.getInt("track_width", 2, 1, 10).slider();
		editorSnapToMarkers = builder.getBoolean("snap_to_markers", false);
		editorSnapToFilmMarkers = builder.getBoolean("snap_to_film_markers", true);
		editorRewind = builder.getBoolean("rewind", true);
		editorStopPlaybackOnScrub = builder.getBoolean("stop_playback_on_scrub", false);
		editorSnapToTicks = builder.getBoolean("snap_to_ticks", true);
		editorRestartOnSeek = builder.getBoolean("restart_on_seek", false);
		editorHorizontalClipEditor = builder.getBoolean("horizontal_clip_editor", false);

		builder.category("workspace", Icons.EDITOR);
		builder.register(editorLayoutSettings = new ValueEditorLayout("layout"));
		editorResizablePanels = builder.getBoolean("resizable_panels", true);
		editorPeriodicSave = builder.getInt("periodic_save", 60, 0, 3600);
		editorMinutesBackup = builder.getBoolean("minutes_backup", true);
		editorKeepFrameOnExit = builder.getBoolean("keep_frame_on_exit", false);
		editorClipAutoName = builder.getBoolean("clip_auto_name", true);

		builder.category("recording", Icons.FILM);
		recordingCountdown = builder.getFloat("countdown", 1.5F, 0F, 30F);
		recordingSwipeDamage = builder.getBoolean("swipe_damage", false);
		recordingOverlays = builder.getBoolean("overlays", true);
		recordingPoseOverlays = builder.getInt("pose_overlays", 0, 0, 42);
		recordingTransformOverlays = builder.getInt("transform_overlays", 0, 0, 42);
		recordingCameraPreview = builder.getBoolean("camera_preview", true);
		recordingTeleport = builder.getBoolean("teleport", true);

		/* Output */
		/* Ordered by how often it gets touched: the resolution first, then the
		 * file, the sound and the frames, and the encoder last */
		builder.category("video", Icons.VIDEO_CAMERA);
		videoWidth = builder.getInt("width", 1280, 2, 8096);
		videoHeight = builder.getInt("height", 720, 2, 8096);
		videoFrameRate = builder.getInt("frame_rate", 60, 10, 1000);
		videoLimitFrameRate = builder.getBoolean("limit_frame_rate", false);
		worldExportResizeWindow = builder.getBoolean("world_export_resize_window", false);
		videoExportPath = builder.getString("export_path", "");
		videoExportFilenameFormat = builder.getString("filename_format", "{datetime}");
		videoExportAudio = builder.getBoolean("audio", false);
		videoExportMinecraftSounds = builder.getBoolean("minecraft_sounds", false);
		videoMuteAudioWhileRender = builder.getBoolean("mute_audio_while_render", false);
		videoMotionBlur = builder.getInt("motion_blur", 0, 0, 6);
		videoHeldFrames = builder.getInt("held_frames", 1, 1, 1000);
		videoDelay = builder.getFloat("delay", 0.5F, 0F, 30F);
		videoOpenFolderAfterExport = builder.getBoolean("open_folder_after_export", false);
		videoPlaySoundAfterExport = builder.getBoolean("play_sound_after_export", true);
		videoEncoderPath = builder.getString("encoder_path", "ffmpeg");
		videoEncoderLog = builder.getBoolean("log", true);
		videoArguments = builder.getString("arguments", DEFAULT_FFMPEG_ARGUMENTS);
		videoArgumentsAudio = builder.getString("arguments_audio", DEFAULT_AUDIO_FFMPEG_ARGUMENTS);
		videoArgumentsMux = builder.getString("arguments_mux", DEFAULT_MUX_FFMPEG_ARGUMENTS);

		builder.category("audio", Icons.SOUND);
		audioWaveformVisibleInPreview = builder.getBoolean("waveform_visible_preview", true);
		audioWaveformVisibleInKeyframes = builder.getBoolean("waveform_visible_keyframes", true);
		audioWaveformDensity = builder.getInt("waveform_density", 20, 10, 100).slider();
		audioWaveformWidth = builder.getFloat("waveform_width", 0.8F, 0F, 1F).slider();
		audioWaveformHeight = builder.getInt("waveform_height", 24, 10, 40).slider();
		audioWaveformFilename = builder.getBoolean("waveform_filename", false);
		audioWaveformTime = builder.getBoolean("waveform_time", false);
		audioWaveformPreviewCombined = builder.getBoolean("waveform_preview_combined", false);

		/* The rest */
		builder.category("model_blocks", Icons.BLOCK);
		renderAllModelBlocks = builder.getBoolean("render_all", true);
		clickModelBlocks = builder.getBoolean("click", true);

		builder.category("cdn", Icons.SERVER);
		cdnUrl = builder.getString("url", "");
		cdnToken = builder.getString("token", "");

		/* Features owning a single option each - a category per switch would mean
		 * a row in the settings list per switch, so they share one. */
		builder.category("misc", Icons.MORE);
		damageControl = builder.getBoolean("damage_control", true);
		shaderCurvesEnabled = builder.getBoolean("shader_curves", true);
		entitySelectorsPropertyWhitelist = builder.getString("entity_selectors_whitelist", "CustomName,Name");
	}
}

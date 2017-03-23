package squidpony.gdx.tests;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import squidpony.squidgrid.GridData;
import squidpony.squidgrid.gui.gdx.SColor;
import squidpony.squidgrid.gui.gdx.SquidColorCenter;
import squidpony.squidgrid.gui.gdx.SquidInput;
import squidpony.squidgrid.gui.gdx.SquidPanel;
import squidpony.squidmath.Noise;
import squidpony.squidmath.SeededNoise;
import squidpony.squidmath.StatefulRNG;

/**
 * Port of Zachary Carter's world generation technique, https://github.com/zacharycarter/mapgen
 * It seems to mostly work now, though it only generates one view of the map that it renders (but biome, moisture, heat,
 * and height maps can all be requested from it).
 */
public class DetailedWorldMapDemo extends ApplicationAdapter {
    public enum  BiomeType {
        Desert,                 // 0
        Savanna,                // 1
        TropicalRainforest,     // 2
        Grassland,              // 3
        Woodland,               // 4
        SeasonalForest,         // 5
        TemperateRainforest,    // 6
        BorealForest,           // 7
        Tundra,                 // 8
        Ice,                    // 9
        Beach,                  // 10
        Rocky                   // 11
    }
    private SpriteBatch batch;
    private SquidColorCenter colorFactory;
    private SquidPanel display;//, overlay;
    private int cellWidth = 1, cellHeight = 1;
    private SquidInput input;
    private static final SColor bgColor = SColor.BLACK;
    private Stage stage;
    private Viewport view;
    private Noise.Noise4D terrain, terrainRidged, heat, moisture, otherRidged;
    private long seed;
    private int iseed;
    private StatefulRNG rng;
    private GridData data;
    private static final int width = 800, height = 800;
    private double[][] heightData = new double[width][height],
            heatData = new double[width][height],
            moistureData = new double[width][height],
            biomeDifferenceData = new double[width][height];
    private int[][] heightCodeData = new int[width][height],
            heatCodeData = new int[width][height],
            moistureCodeData = new int[width][height],
            biomeUpperCodeData = new int[width][height], 
            biomeLowerCodeData = new int[width][height];
    public double waterModifier = 0.0, coolingModifier = 1.0;

    public static final double
            deepWaterLower = -1.0, deepWaterUpper = -0.7,        // -4
            mediumWaterLower = -0.7, mediumWaterUpper = -0.3,    // -3
            shallowWaterLower = -0.3, shallowWaterUpper = -0.1,  // -2
            coastalWaterLower = -0.1, coastalWaterUpper = 0.1,   // -1
            sandLower = 0.1, sandUpper = 0.22,                   // 0
            grassLower = 0.22, grassUpper = 0.35,                // 1
            forestLower = 0.35, forestUpper = 0.6,               // 2
            rockLower = 0.6, rockUpper = 0.8,                    // 3
            snowLower = 0.8, snowUpper = 1.0;                    // 4

    public static final double[] lowers = {deepWaterLower, mediumWaterLower, shallowWaterLower, coastalWaterLower,
            sandLower, grassLower, forestLower, rockLower, snowLower},
            differences = {deepWaterUpper - deepWaterLower, mediumWaterUpper - mediumWaterLower,
            shallowWaterUpper - shallowWaterLower, coastalWaterUpper - coastalWaterLower, sandUpper - sandLower,
                    grassUpper - grassLower, forestUpper - forestLower, rockUpper - rockLower, snowUpper - snowLower};



    public static final double
            coldestValueLower = 0.0,   coldestValueUpper = 0.15, // 0
            colderValueLower = 0.15,   colderValueUpper = 0.31,  // 1
            coldValueLower = 0.31,     coldValueUpper = 0.5,     // 2
            warmValueLower = 0.5,      warmValueUpper = 0.69,     // 3
            warmerValueLower = 0.69,    warmerValueUpper = 0.85,   // 4
            warmestValueLower = 0.85,   warmestValueUpper = 1.0,  // 5

            driestValueLower = 0.0,    driestValueUpper  = 0.27, // 0
            drierValueLower = 0.27,    drierValueUpper   = 0.4,  // 1
            dryValueLower = 0.4,       dryValueUpper     = 0.6,  // 2
            wetValueLower = 0.6,       wetValueUpper     = 0.8,  // 3
            wetterValueLower = 0.8,    wetterValueUpper  = 0.9,  // 4
            wettestValueLower = 0.9,   wettestValueUpper = 1.0;  // 5

    private static SquidColorCenter squidColorCenter = new SquidColorCenter();

    private static float black = SColor.floatGetI(0, 0, 0),
            white = SColor.floatGet(0xffffffff);
    // Biome map colors

    private static float ice = SColor.ALICE_BLUE.toFloatBits();
    private static float darkIce = SColor.lerpFloatColors(ice, black, 0.15f);

    private static float desert = SColor.floatGetI(238, 218, 130);
    private static float darkDesert = SColor.lerpFloatColors(desert, black, 0.15f);

    private static float savanna = SColor.floatGetI(177, 209, 110);
    private static float darkSavanna = SColor.lerpFloatColors(savanna, black, 0.15f);

    private static float tropicalRainforest = SColor.floatGetI(66, 123, 25);
    private static float darkTropicalRainforest = SColor.lerpFloatColors(tropicalRainforest, black, 0.15f);

    private static float tundra = SColor.floatGetI(96, 131, 112);
    private static float darkTundra = SColor.lerpFloatColors(tundra, black, 0.15f);

    private static float temperateRainforest = SColor.floatGetI(29, 73, 40);
    private static float darkTemperateRainforest = SColor.lerpFloatColors(temperateRainforest, black, 0.15f);

    private static float grassland = SColor.floatGetI(164, 225, 99);
    private static float darkGrassland = SColor.lerpFloatColors(grassland, black, 0.15f);

    private static float seasonalForest = SColor.floatGetI(73, 100, 35);
    private static float darkSeasonalForest = SColor.lerpFloatColors(seasonalForest, black, 0.15f);

    private static float borealForest = SColor.floatGetI(95, 115, 62);
    private static float darkBorealForest = SColor.lerpFloatColors(borealForest, black, 0.15f);

    private static float woodland = SColor.floatGetI(139, 175, 90);
    private static float darkWoodland = SColor.lerpFloatColors(woodland, black, 0.15f);

    private static float rocky = SColor.floatGetI(177, 167, 157);
    private static float darkRocky = SColor.lerpFloatColors(rocky, black, 0.15f);

    private static float beach = SColor.floatGetI(250, 225, 165);
    private static float darkBeach = SColor.lerpFloatColors(beach, black, 0.15f);

    // water colors
    private static float deepColor = SColor.floatGetI(0, 68, 128);
    private static float darkDeepColor = SColor.lerpFloatColors(deepColor, black, 0.15f);
    private static float mediumColor = SColor.floatGetI(0, 89, 159);
    private static float darkMediumColor = SColor.lerpFloatColors(mediumColor, black, 0.15f);
    private static float shallowColor = SColor.CERULEAN.toFloatBits();
    private static float darkShallowColor = SColor.lerpFloatColors(shallowColor, black, 0.15f);
    private static float coastalColor = SColor.lerpFloatColors(shallowColor, white, 0.3f);
    private static float darkCoastalColor = SColor.lerpFloatColors(coastalColor, black, 0.15f);
    private static float foamColor = SColor.floatGetI(161, 252, 255);
    private static float darkFoamColor = SColor.lerpFloatColors(foamColor, black, 0.15f);

    private static float iceWater = SColor.floatGetI(210, 255, 252);
    private static float coldWater = mediumColor;
    private static float riverWater = shallowColor;

    private static float riverColor = SColor.floatGetI(30, 120, 200);
    private static float sandColor = SColor.floatGetI(240, 240, 64);
    private static float grassColor = SColor.floatGetI(50, 220, 20);
    private static float forestColor = SColor.floatGetI(16, 160, 0);
    private static float rockColor = SColor.floatGetI(177, 167, 157);
    private static float snowColor = SColor.floatGetI(255, 255, 255);

    // Heat map colors
    private static float coldest = SColor.floatGetI(0, 255, 255);
    private static float colder = SColor.floatGetI(170, 255, 255);
    private static float cold = SColor.floatGetI(0, 229, 133);
    private static float warm = SColor.floatGetI(255, 255, 100);
    private static float warmer = SColor.floatGetI(255, 100, 0);
    private static float warmest = SColor.floatGetI(241, 12, 0);

    // Moisture map colors
    private static float dryest = SColor.floatGetI(255, 139, 17);
    private static float dryer = SColor.floatGetI(245, 245, 23);
    private static float dry = SColor.floatGetI(80, 255, 0);
    private static float wet = SColor.floatGetI(85, 255, 255);
    private static float wetter = SColor.floatGetI(20, 70, 255);
    private static float wettest = SColor.floatGetI(0, 0, 100);

    private static float[] biomeColors = {
            desert,
            savanna,
            tropicalRainforest,
            grassland,
            woodland,
            seasonalForest,
            temperateRainforest,
            borealForest,
            tundra,
            ice,
            beach,
            rocky
    }, biomeDarkColors = {
            darkDesert,
            darkSavanna,
            darkTropicalRainforest,
            darkGrassland,
            darkWoodland,
            darkSeasonalForest,
            darkTemperateRainforest,
            darkBorealForest,
            darkTundra,
            darkIce,
            darkBeach,
            darkRocky
    };

    public static int codeHeight(final double high)
    {
        if(high < deepWaterUpper)
            return 0;
        if(high < mediumWaterUpper)
            return 1;
        if(high < shallowWaterUpper)
            return 2;
        if(high < coastalWaterUpper)
            return 3;
        if(high < sandUpper)
            return 4;
        if(high < grassUpper)
            return 5;
        if(high < forestUpper)
            return 6;
        if(high < rockUpper)
            return 7;
        return 8;
    }
    public static int codeHeat(final double hot)
    {
        if(hot < coldestValueUpper)
            return 0;
        if(hot < colderValueUpper)
            return 1;
        if(hot < coldValueUpper)
            return 2;
        if(hot < warmValueUpper)
            return 3;
        if(hot < warmerValueUpper)
            return 4;
        return 5;
    }
    public static int codeMoisture(final double moist)
    {
        if(moist < driestValueUpper)
            return 0;
        if(moist < drierValueUpper)
            return 1;
        if(moist < dryValueUpper)
            return 2;
        if(moist < wetValueUpper)
            return 3;
        if(moist < wetterValueUpper)
            return 4;
        return 5;
    }

    protected final static BiomeType[] BIOME_TABLE = {
            //COLDEST        //COLDER          //COLD                  //HOT                          //HOTTER                       //HOTTEST
            BiomeType.Ice,   BiomeType.Tundra, BiomeType.Grassland,    BiomeType.Desert,              BiomeType.Desert,              BiomeType.Desert,              //DRYEST
            BiomeType.Ice,   BiomeType.Tundra, BiomeType.Grassland,    BiomeType.Grassland,           BiomeType.Desert,              BiomeType.Desert,              //DRYER
            BiomeType.Ice,   BiomeType.Tundra, BiomeType.Woodland,     BiomeType.Woodland,            BiomeType.Savanna,             BiomeType.Savanna,             //DRY
            BiomeType.Ice,   BiomeType.Tundra, BiomeType.BorealForest, BiomeType.SeasonalForest,      BiomeType.Savanna,             BiomeType.Savanna,             //WET
            BiomeType.Ice,   BiomeType.Tundra, BiomeType.BorealForest, BiomeType.SeasonalForest,      BiomeType.TropicalRainforest,  BiomeType.TropicalRainforest,  //WETTER
            BiomeType.Ice,   BiomeType.Tundra, BiomeType.BorealForest, BiomeType.TemperateRainforest, BiomeType.TropicalRainforest,  BiomeType.TropicalRainforest   //WETTEST
    };

    protected static int codeBiomeUpper(double hot, double moist)
    {
        /*
        int m = 5, h = 5;
        if(moist < driestValueUpper + (drierValueUpper - drierValueLower) * 0.5)
            m = 0;
        else if(moist < drierValueUpper + (dryValueUpper - dryValueLower) * 0.5)
            m = 1;
        else if(moist < dryValueUpper + (wetValueUpper - wetValueLower) * 0.5)
            m = 2;
        else if(moist < wetValueUpper + (wetterValueUpper - wetterValueLower) * 0.5)
            m = 3;
        else if(moist < wetterValueUpper + (wettestValueUpper - wettestValueLower) * 0.5)
            m = 4;

        if(hot < coldestValueUpper + (colderValueUpper - colderValueLower) * 0.5)
            h = 0;
        else if(hot < colderValueUpper + (coldValueUpper - coldValueLower) * 0.5)
            h = 1;
        else if(hot < coldValueUpper + (warmValueUpper - warmValueLower) * 0.5)
            h = 2;
        else if(hot < warmValueUpper + (warmerValueUpper - warmerValueLower) * 0.5)
            h = 3;
        else if(hot < warmerValueUpper + (warmestValueUpper - warmestValueLower) * 0.5)
            h = 4;
            */
        return BIOME_TABLE[codeHeat(hot) + codeMoisture(moist) * 6].ordinal();
    }

    private void codeBiome(int x, int y, double hot, double moist, int heightCode) {
        int hc = 5, mc = 5;
        double upperProximity, lowerProximity;
        if(hot < coldestValueUpper)
        {
            hc = 0;
            upperProximity = (hot - coldestValueLower) / (coldestValueUpper - coldestValueLower);
        }
        else if(hot < colderValueUpper)
        {
            hc = 1;
            upperProximity = (hot - colderValueLower) / (colderValueUpper - colderValueLower);
        }
        else if(hot < coldValueUpper)
        {
            hc = 2;
            upperProximity = (hot - coldValueLower) / (coldValueUpper - coldValueLower);
        }
        else if(hot < warmValueUpper)
        {
            hc = 3;
            upperProximity = (hot - warmValueLower) / (warmValueUpper - warmValueLower);
        }
        else if(hot < warmerValueUpper)
        {
            hc = 4;
            upperProximity = (hot - warmerValueLower) / (warmerValueUpper - warmerValueLower);
        }
        else
        {
            upperProximity = (hot - warmestValueLower) / (warmestValueUpper - warmestValueLower);
        }

        if(moist < driestValueUpper)
        {
            mc = 0;
            upperProximity += (moist - driestValueLower) / (driestValueUpper - driestValueLower);
        }
        else if(moist < drierValueUpper)
        {
            mc = 1;
            upperProximity += (moist - drierValueLower) / (drierValueUpper - drierValueLower);
        }
        else if(moist < dryValueUpper)
        {
            mc = 2;
            upperProximity += (moist - dryValueLower) / (dryValueUpper - dryValueLower);
        }
        else if(moist < wetValueUpper)
        {
            mc = 3;
            upperProximity += (moist - wetValueLower) / (wetValueUpper - wetValueLower);
        }
        else if(moist < wetterValueUpper)
        {
            mc = 4;
            upperProximity += (moist - wetterValueLower) / (wetterValueUpper - wetterValueLower);
        }
        else
        {
            upperProximity += (moist - wettestValueLower) / (wettestValueUpper - wettestValueLower);
        }

        heatCodeData[x][y] = hc;
        moistureCodeData[x][y] = mc;
        biomeUpperCodeData[x][y] = heightCode == 4 ? (hc == 1 ? 11 : 10) : BIOME_TABLE[hc + mc * 6].ordinal();

        mc = 0;
        hc = 0;
        double bound;
        if(moist >= (bound = wetterValueUpper - (wetterValueUpper - wetterValueLower) * 0.5))
        {
            mc = 5;
            lowerProximity = (moist - bound) / (wettestValueUpper - bound);
        }
        else if(moist >= (bound = wetValueUpper - (wetValueUpper - wetValueLower) * 0.5))
        {
            mc = 4;
            lowerProximity = (moist - bound) / (wetterValueUpper - bound);
        }
        else if(moist >= (bound = dryValueUpper - (dryValueUpper - dryValueLower) * 0.5))
        {
            mc = 3;
            lowerProximity = (moist - bound) / (wetValueUpper - bound);
        }
        else if(moist >= (bound = drierValueUpper - (drierValueUpper - drierValueLower) * 0.5))
        {
            mc = 2;
            lowerProximity = (moist - bound) / (dryValueUpper - bound);
        }
        else if(moist >= (bound = driestValueUpper - (driestValueUpper - driestValueLower) * 0.5))
        {
            mc = 1;
            lowerProximity = (moist - bound) / (drierValueUpper - bound);
        }
        else
        {
            lowerProximity = (moist) / (driestValueUpper);
        }

        if(hot >= (bound = warmerValueUpper - (warmerValueUpper - warmerValueLower) * 0.5))
        {
            hc = 5;
            lowerProximity += (hot - bound) / (warmestValueUpper - bound);
        }
        else if(hot >= (bound = warmValueUpper - (warmValueUpper - warmValueLower) * 0.5))
        {
            hc = 4;
            lowerProximity += (hot - bound) / (warmerValueUpper - bound);
        }
        else if(hot >= (bound = coldValueUpper - (coldValueUpper - coldValueLower) * 0.5))
        {
            hc = 3;
            lowerProximity += (hot - bound) / (warmValueUpper - bound);
        }
        else if(hot >= (bound = colderValueUpper - (colderValueUpper - colderValueLower) * 0.5))
        {
            hc = 2;
            lowerProximity += (hot - bound) / (coldValueUpper - bound);
        }
        else if(hot >= (bound = coldestValueUpper - (coldestValueUpper - coldestValueLower) * 0.5))
        {
            hc = 1;
            lowerProximity += (hot - bound) / (colderValueUpper - bound);
        }
        else
        {
            lowerProximity += (hot) / (coldestValueUpper);
        }

        biomeDifferenceData[x][y] = (upperProximity - lowerProximity + 2.0) * 0.25;
        biomeLowerCodeData[x][y] = BIOME_TABLE[hc + mc * 6].ordinal();
    }
    
    protected static int codeBiomeLower(double hot, double moist)
    {
        int m = 0, h = 0;
        if(moist >= wetterValueUpper - (wetterValueUpper - wetterValueLower) * 0.5)
            m = 5;
        else if(moist >= wetValueUpper - (wetValueUpper - wetValueLower) * 0.5)
            m = 4;
        else if(moist >= dryValueUpper - (dryValueUpper - dryValueLower) * 0.5)
            m = 3;
        else if(moist >= drierValueUpper - (drierValueUpper - drierValueLower) * 0.5)
            m = 2;
        else if(moist >= driestValueUpper - (driestValueUpper - driestValueLower) * 0.5)
            m = 1;

        if(hot >= warmerValueUpper - (warmerValueUpper - warmerValueLower) * 0.5)
            h = 5;
        else if(hot >= warmValueUpper - (warmValueUpper - warmValueLower) * 0.5)
            h = 4;
        else if(hot >= coldValueUpper - (coldValueUpper - coldValueLower) * 0.5)
            h = 3;
        else if(hot >= colderValueUpper - (colderValueUpper - colderValueLower) * 0.5)
            h = 2;
        else if(hot >= coldestValueUpper - (coldestValueUpper - coldestValueLower) * 0.5)
            h = 1;
        return BIOME_TABLE[h + m * 6].ordinal();

    }
    
    private int heightIndex = -1, heatIndex = -1, moistureIndex = -1;
    @Override
    public void create() {
        batch = new SpriteBatch();
        display = new SquidPanel(width, height, cellWidth, cellHeight);
        //overlay = new SquidPanel(16, 8, DefaultResources.getStretchableFont().width(32).height(64).initBySize());
        colorFactory = new SquidColorCenter();
        view = new StretchViewport(width, height);
        stage = new Stage(view, batch);
        seed = 0xBEEFF00DCAFECABAL;
        rng = new StatefulRNG(seed); //seed
        terrain = new Noise.Layered4D(new SeededNoise(iseed = rng.nextInt()), 6, 1.9);
        terrainRidged = new Noise.Ridged4D(new SeededNoise(iseed = rng.nextInt()), 4, 2.0);
        heat = new Noise.Layered4D(new SeededNoise(rng.nextInt()), 5, 2.0);
        moisture = new Noise.Layered4D(new SeededNoise(rng.nextInt()), 4, 5.5);
        otherRidged = new Noise.Ridged4D(new SeededNoise(iseed = rng.nextInt()), 4, 1.5);
        data = new GridData(16);
        regenerate();
        input = new SquidInput(new SquidInput.KeyHandler() {
            @Override
            public void handle(char key, boolean alt, boolean ctrl, boolean shift) {
                switch (key) {
                    case SquidInput.ENTER:
                        regenerate();
                        //putMap();
                        Gdx.graphics.requestRendering();
                        break;
                    case 'Q':
                    case 'q':
                    case SquidInput.ESCAPE: {
                        Gdx.app.exit();
                    }
                }
            }
        });
        Gdx.input.setInputProcessor(input);
        display.setPosition(0, 0);
        stage.addActor(display);
        //putMap();
        Gdx.graphics.setContinuousRendering(false);
        Gdx.graphics.requestRendering();
    }
    public void regenerate()
    {
        double minHeight = Double.POSITIVE_INFINITY, maxHeight = Double.NEGATIVE_INFINITY,
                minHeat = Double.POSITIVE_INFINITY, maxHeat = Double.NEGATIVE_INFINITY,
                minHeat2 = Double.POSITIVE_INFINITY, maxHeat2 = Double.NEGATIVE_INFINITY,
                minWet = Double.POSITIVE_INFINITY, maxWet = Double.NEGATIVE_INFINITY;
        int seedA = rng.nextInt(), seedB = rng.nextInt(), seedC = rng.nextInt(), seedD = rng.nextInt(), t;
        waterModifier = rng.nextDouble(0.15)-0.06;
        coolingModifier = (rng.nextDouble(0.75) - rng.nextDouble(0.75) + 1.0);

        double p, q,
                ps, pc,
                qs, qc,
                h, temp,
                i_w = 6.283185307179586 / width, i_h = 6.283185307179586 / height;;
        for (int y = 0; y < height; y++) {
            q = y * i_h;
            qs = Math.sin(q);
            qc = Math.cos(q);
            for (int x = 0; x < width; x++) {
                p = x * i_w;
                ps = Math.sin(p);
                pc = Math.cos(p);
                h = terrain.getNoiseWithSeed(pc +
                                terrainRidged.getNoiseWithSeed(pc, ps, qc, qs, seedC),
                        ps, qc, qs, seedA);
                p = Math.signum(h) + waterModifier;
                h *= p * p;
                heightData[x][y] = h;
                minHeight = Math.min(minHeight, h);
                maxHeight = Math.max(maxHeight, h);
                heatData[x][y] = (h = heat.getNoiseWithSeed(pc, ps, qc
                        + otherRidged.getNoiseWithSeed(pc, ps, qc, qs, seedD + seedC), qs, seedB));
                minHeat = Math.min(minHeat, h);
                maxHeat = Math.max(maxHeat, h);
                moistureData[x][y] = (h = moisture.getNoiseWithSeed(pc, ps, qc, qs
                        + otherRidged.getNoiseWithSeed(pc, ps, qc, qs, seedD + seedB), seedC));
                minWet = Math.min(minWet, h);
                maxWet = Math.max(maxWet, h);
            }
        }
        double heightDiff = 2.0 / (maxHeight - minHeight),
                heatDiff = 0.8 / (maxHeat - minHeat),
                wetDiff = 1.0 / (maxWet - minWet),
                hMod,
                halfHeight = (height - 1) * 0.5, i_half = 1.0 / halfHeight;
        for (int y = 0; y < height; y++) {
            temp = Math.abs(y - halfHeight) * i_half;
            temp *= (2.4 - temp);
            for (int x = 0; x < width; x++) {
                heightData[x][y] = (h = (heightData[x][y] - minHeight) * heightDiff - 1.0);
                heightCodeData[x][y] = (t = codeHeight(h));
                hMod = 1.0;
                switch (t){
                    case 0:
                    case 1:
                    case 2:
                    case 3:
                        h = 0.4;
                        hMod = 0.2;
                        break;
                    case 6: h = -0.1 * (h - forestLower - 0.08);
                        break;
                    case 7: h *= -0.25;
                        break;
                    case 8: h *= -0.4;
                        break;
                    default: h *= 0.05;
                }
                heatData[x][y] = (h = (((heatData[x][y] - minHeat) * heatDiff * hMod) + h + 0.6) * (2.2 - temp));
                minHeat2 = Math.min(minHeat2, h);
                maxHeat2 = Math.max(maxHeat2, h);
            }
        }
        heatDiff = coolingModifier / (maxHeat2 - minHeat2);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                t = heightCodeData[x][y];
                h = ((heatData[x][y] - minHeat2) * heatDiff);
                heatData[x][y] = (h = Math.pow(h, 2.0 - h * 2.0));
                moistureData[x][y] = (q = (moistureData[x][y] - minWet) * wetDiff);
                codeBiome(x, y, h, q, t);
                /*
                if(p == 4)
                    biomeUpperCodeData[x][y] = (t == 1) ? 11 : 10;
                else
                    biomeUpperCodeData[x][y] = codeBiomeUpper(h, q);
                biomeLowerCodeData[x][y] = codeBiomeLower(h, q);
                */
            }
           
        }
        heightIndex = data.putDoubles("height", heightData);
        heatIndex = data.putDoubles("heat", heatData);
        moistureIndex = data.putDoubles("moisture", moistureData);

    }

    public void putMap() {
        display.erase();
        int hc, buc, blc, tc;
        double h;
        for (int y = 0; y < height; y++) {
            PER_CELL:
            for (int x = 0; x < width; x++) {
                h = heightData[x][y];
                hc = heightCodeData[x][y];
                buc = biomeUpperCodeData[x][y];
                blc = biomeLowerCodeData[x][y];
                tc = heatCodeData[x][y];
                if(tc == 0)
                {
                    switch (hc)
                    {
/*
                        case 0:
                            display.put(x, y, SColor.lerpFloatColors(shallowColor, darkShallowColor,
                                    (float) ((h - lowers[hc]) / (differences[hc] * 1.6))));
                            continue PER_CELL;
                        case 1:
                            display.put(x, y, SColor.lerpFloatColors(coastalColor, darkCoastalColor,
                                    (float) ((h - lowers[hc]) / (differences[hc] * 1.4))));
                            continue PER_CELL;
                        case 2:
                            display.put(x, y, SColor.lerpFloatColors(ice, coastalColor,
                                    (float) ((h - lowers[hc]) / (differences[hc] * 1.2))));
                            continue PER_CELL;
                        case 3:
                            display.put(x, y, SColor.lerpFloatColors(ice, coastalColor,
                                    (float) ((h - lowers[hc]) / (differences[hc]))));
                            continue PER_CELL;
                        case 4:
                        */
                        case 0:
                        case 1:
                        case 2:
                        case 3:
                            display.put(x, y, SColor.lerpFloatColors(shallowColor, ice,
                                    (float) ((h - deepWaterLower) / (coastalWaterUpper - deepWaterLower))));
                            continue PER_CELL;
                        case 4:
                            display.put(x, y, SColor.lerpFloatColors(darkIce, ice,
                                    (float) ((h - lowers[hc]) / (differences[hc]))));
                            continue PER_CELL;

                    }
                }
                switch (hc) {
                    case 0:
                    case 1:
                    case 2:
                    case 3:
                        display.put(x, y, SColor.lerpFloatColors(deepColor, coastalColor,
                                (float) ((h - deepWaterLower) / (coastalWaterUpper - deepWaterLower))));
                        break;
                    /*
                    case 0:
                        display.put(x, y, SColor.lerpFloatColors(darkDeepColor, deepColor,
                                (float) ((h - lowers[hc]) / (differences[hc]))));
                        break;
                    case 1:
                        display.put(x, y, SColor.lerpFloatColors(darkMediumColor, mediumColor,
                                (float) ((h - lowers[hc]) / (differences[hc]))));
                        break;
                    case 2:
                        display.put(x, y, SColor.lerpFloatColors(darkShallowColor, shallowColor,
                                (float) ((h - lowers[hc]) / (differences[hc]))));
                        break;
                    case 3:
                        display.put(x, y, SColor.lerpFloatColors(darkCoastalColor, coastalColor,
                                (float) ((h - lowers[hc]) / (differences[hc]))));
                        break;
                        */
                    /*
                    case 4:
                        if(tc == 1)
                            display.put(x, y, SColor.lerpFloatColors(darkRocky, rocky,
                                    (float) ((h - lowers[hc]) / (differences[hc]))));
                        else
                            display.put(x, y, SColor.lerpFloatColors(darkBeach, beach,
                                (float) ((h - lowers[hc]) / (differences[hc]))));
                        break;*/
                    default: {
                        display.put(x, y, SColor.lerpFloatColors(biomeColors[buc], biomeColors[blc],
                                (float) biomeDifferenceData[x][y]));
                                //(float) ((h - lowers[hc]) / (differences[hc]))));
                        /*
                        switch (bc) {
                            case 0: //Desert
                                display.put(x, y, SColor.lerpFloatColors(darkDesert, desert,
                                        (float) ((h - lowers[hc]) / (differences[hc]))));
                                break;
                            case 1: //Savanna
                                display.put(x, y, SColor.lerpFloatColors(darkSavanna, savanna,
                                        (float) ((h - lowers[hc]) / (differences[hc]))));
                                break;
                            case 2: //TropicalRainforest
                                display.put(x, y, SColor.lerpFloatColors(darkTropicalRainforest, tropicalRainforest,
                                        (float) ((h - lowers[hc]) / (differences[hc]))));
                                break;
                            case 3: //Grassland
                                display.put(x, y, SColor.lerpFloatColors(darkGrassland, grassland,
                                        (float) ((h - lowers[hc]) / (differences[hc]))));
                                break;
                            case 4: //Woodland
                                display.put(x, y, SColor.lerpFloatColors(darkWoodland, woodland,
                                        (float) ((h - lowers[hc]) / (differences[hc]))));
                                break;
                            case 5: //SeasonalForest
                                display.put(x, y, SColor.lerpFloatColors(darkSeasonalForest, seasonalForest,
                                        (float) ((h - lowers[hc]) / (differences[hc]))));
                                break;
                            case 6: //TemperateRainforest
                                display.put(x, y, SColor.lerpFloatColors(darkTemperateRainforest, temperateRainforest,
                                        (float) ((h - lowers[hc]) / (differences[hc]))));
                                break;
                            case 7: //BorealForest
                                display.put(x, y, SColor.lerpFloatColors(darkBorealForest, borealForest,
                                        (float) ((h - lowers[hc]) / (differences[hc]))));
                                break;
                            case 8: //Tundra
                                display.put(x, y, SColor.lerpFloatColors(darkTundra, tundra,
                                        (float) ((h - lowers[hc]) / (differences[hc]))));
                                break;
                            case 9: //Ice
                                display.put(x, y, SColor.lerpFloatColors(darkIce, ice,
                                        (float) ((h - lowers[hc]) / (differences[hc]))));
                                break;
                        }
                        */
                    }
                }
            }
        }
    }

    @Override
    public void render() {
        // standard clear the background routine for libGDX
        Gdx.gl.glClearColor(0f, 0f, 0f, 1.0f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        // not sure if this is always needed...
        Gdx.gl.glDisable(GL20.GL_BLEND);
        // need to display the map every frame, since we clear the screen to avoid artifacts.
        putMap();
        // if the user clicked, we have a list of moves to perform.

        // if we are waiting for the player's input and get input, process it.
        if (input.hasNext()) {
            input.next();
        }
        // stage has its own batch and must be explicitly told to draw().
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        view.update(width, height, true);
        view.apply(true);
    }

    public static void main(String[] arg) {
        LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
        config.title = "SquidLib Test: Detailed World Map";
        config.width = width;
        config.height = height;
        config.foregroundFPS = 0;
        config.addIcon("Tentacle-16.png", Files.FileType.Internal);
        config.addIcon("Tentacle-32.png", Files.FileType.Internal);
        config.addIcon("Tentacle-128.png", Files.FileType.Internal);
        new LwjglApplication(new DetailedWorldMapDemo(), config);
    }
}
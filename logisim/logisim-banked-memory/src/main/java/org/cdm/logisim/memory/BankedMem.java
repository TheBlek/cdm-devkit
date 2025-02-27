package org.cdm.logisim.memory;

import java.awt.Color;
import java.awt.Graphics;
import java.io.File;
import java.io.IOException;
import java.util.WeakHashMap;

import com.cburch.hex.HexModel;
import com.cburch.hex.HexModelListener;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.gui.hex.HexFile;
import com.cburch.logisim.gui.hex.HexFrame;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.MenuExtender;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.tools.key.JoinedConfigurator;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.util.StringUtil;

abstract class BankedMem extends InstanceFactory {
    // Note: The code is meant to be able to handle up to 32-bit addresses, but it
    // hasn't been debugged thoroughly. There are two definite changes I would
    // make if I were to extend the address bits: First, there would need to be some
    // modification to the memory's graphical representation, because there isn't
    // room in the box to include such long memory addresses with the current font
    // size. And second, I'd alter the MemContents class's PAGE_SIZE_BITS constant
    // to 14 so that its "page table" isn't quite so big.
    public static final Attribute<BitWidth> ADDR_ATTR = Attributes.forBitWidth(
            "addrWidth", BankedStrings.getter("ramAddrWidthAttr"), 2, 24);
    public static final Attribute<BitWidth> DATA_ATTR = Attributes.forBitWidth(
            "dataWidth", BankedStrings.getter("ramDataWidthAttr"));

    public static final Attribute<String> PATH_ATTRIBUTE = Attributes.forString("Directory", BankedStrings.getter("Image file"));

    public static final AttributeOption HAS_IMAGE_FILE = new AttributeOption("load", BankedStrings.getter("Yes"));
    public static final AttributeOption NO_IMAGE_FILE = new AttributeOption("noLoading", BankedStrings.getter("No"));
    public static final Attribute<AttributeOption> LOAD_FROM_IMAGE_FILE =
            Attributes.forOption("LoadImageFromFile",
                    BankedStrings.getter("Load Image From File"),
                    new AttributeOption[]{HAS_IMAGE_FILE, NO_IMAGE_FILE});

    // port-related constants
    static final int DATA = 0;
    static final int ADDR = 1;
    static final int CS = 2;
    static final int MEM_INPUTS = 3;

    public static final int DEFAULT_BITS_SIZE = 1;
    public static final int DEFAULT_BITS_VALUE = 0;

    // other constants
    static final int DELAY = 10;

    private WeakHashMap<Instance, File> currentInstanceFiles;

    BankedMem(String name, StringGetter desc, int extraPorts) {

        super(name, desc);
        currentInstanceFiles = new WeakHashMap<Instance, File>();
        setInstancePoker(BankedMemPoker.class);
        setKeyConfigurator(JoinedConfigurator.create(
                new BitWidthConfigurator(ADDR_ATTR, 2, 24, 0),
                new BitWidthConfigurator(DATA_ATTR)));

        setOffsetBounds(Bounds.create(-140, -40, 140, 80));
    }

    abstract void configurePorts(Instance instance);

    @Override
    public abstract AttributeSet createAttributeSet();

    abstract BankedMemState getState(InstanceState state);

    abstract BankedMemState getState(Instance instance, CircuitState state);

    abstract HexFrame getHexFrame(Project proj, Instance instance, CircuitState state);

    @Override
    public abstract void propagate(InstanceState state);

    @Override
    protected void configureNewInstance(Instance instance) {
        configurePorts(instance);
    }

    void configureStandardPorts(Instance instance, Port[] ps) {
        ps[DATA] = new Port(0, 0, Port.INOUT, DATA_ATTR);
        ps[ADDR] = new Port(-140, 0, Port.INPUT, ADDR_ATTR);
        ps[CS] = new Port(-90, 40, Port.INPUT, 1);
        ps[DATA].setToolTip(BankedStrings.getter("memDataTip"));
        ps[ADDR].setToolTip(BankedStrings.getter("memAddrTip"));
        ps[CS].setToolTip(BankedStrings.getter("memCSTip"));
    }

    @Override
    public void paintInstance(InstancePainter painter) {
        Graphics g = painter.getGraphics();
        Bounds bds = painter.getBounds();

        // draw boundary
        painter.drawBounds();

        // draw contents
        if (painter.getShowState()) {
            BankedMemState state = getState(painter);
            state.paint(painter.getGraphics(), bds.getX(), bds.getY());
        } else {
            BitWidth addr = painter.getAttributeValue(ADDR_ATTR);
            int addrBits = addr.getWidth();
            int bytes = 1 << addrBits;
            String label;
            if (this instanceof BankedROM) {

                if (addrBits >= 30) {
                    label = StringUtil.format(BankedStrings.get("romGigabyteLabel"), ""
                            + (bytes >>> 30));
                } else if (addrBits >= 20) {
                    label = StringUtil.format(BankedStrings.get("romMegabyteLabel"), ""
                            + (bytes >> 20));
                } else if (addrBits >= 10) {
                    label = StringUtil.format(BankedStrings.get("romKilobyteLabel"), ""
                            + (bytes >> 10));
                } else {
                    label = StringUtil.format(BankedStrings.get("romByteLabel"), ""
                            + bytes);
                }
            } else {
                if (addrBits >= 30) {
                    label = StringUtil.format(BankedStrings.get("ramGigabyteLabel"), ""
                            + (bytes >>> 30));
                } else if (addrBits >= 20) {
                    label = StringUtil.format(BankedStrings.get("ramMegabyteLabel"), ""
                            + (bytes >> 20));
                } else if (addrBits >= 10) {
                    label = StringUtil.format(BankedStrings.get("ramKilobyteLabel"), ""
                            + (bytes >> 10));
                } else {
                    label = StringUtil.format(BankedStrings.get("ramByteLabel"), ""
                            + bytes);
                }
            }
            GraphicsUtil.drawCenteredText(g, label, bds.getX() + bds.getWidth()
                    / 2, bds.getY() + bds.getHeight() / 2);
        }

        // draw input and output ports
        painter.drawPort(DATA, BankedStrings.get("ramDataLabel"), Direction.WEST);
        painter.drawPort(ADDR, BankedStrings.get("ramAddrLabel"), Direction.EAST);
        g.setColor(Color.GRAY);
        painter.drawPort(CS, BankedStrings.get("ramCSLabel"), Direction.SOUTH);

        if (this instanceof BankedROM) {
            painter.drawPort(BankedROM.BITS, BankedStrings.get("bit"), Direction.SOUTH);
        } else if (this instanceof BankedRAM) {
            painter.drawPort(BankedRAM.BITS, BankedStrings.get("bit"), Direction.SOUTH);
        }
    }

    File getCurrentImage(Instance instance) {
        return currentInstanceFiles.get(instance);
    }

    void setCurrentImage(Instance instance, File value) {
        currentInstanceFiles.put(instance, value);
    }

    public void loadImage(InstanceState instanceState, File imageFile)
            throws IOException {
        instanceState.getAttributeSet().setValue(PATH_ATTRIBUTE, imageFile.getAbsolutePath());
        BankedMemState s = this.getState(instanceState);
        HexFile.open(s.getContents(), imageFile);
        this.setCurrentImage(instanceState.getInstance(), imageFile);
    }

    void autoLoadImage(InstanceState state) {
        if (state.getAttributeSet().getValue(LOAD_FROM_IMAGE_FILE).equals(NO_IMAGE_FILE)){
            return;
        }
        String filename = state.getAttributeSet().getValue(PATH_ATTRIBUTE);
        try {
            File file = new File(filename);
            loadImage(state, file);
        } catch (IOException e) {
        }
    }

    @Override
    protected Object getInstanceFeature(Instance instance, Object key) {
        if (key == MenuExtender.class) return new BankedMemMenu(this, instance);
        return super.getInstanceFeature(instance, key);
    }

    static class MemListener implements HexModelListener {
        Instance instance;

        MemListener(Instance instance) {
            this.instance = instance;
        }

        public void metainfoChanged(HexModel source) {
        }

        public void bytesChanged(HexModel source, long start,
                                 long numBytes, int[] values) {
            instance.fireInvalidated();
        }
    }
}


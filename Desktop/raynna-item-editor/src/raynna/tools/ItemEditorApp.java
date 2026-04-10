package raynna.tools;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.intellijthemes.FlatOneDarkIJTheme;
import raynna.tools.itemeditor.ItemDefinitionRecord;
import raynna.tools.itemeditor.ItemDefinitionsService;
import raynna.tools.itemeditor.ItemDefinitionsService.ItemListEntry;
import raynna.tools.itemeditor.render.ItemModelRenderer;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ItemEditorApp {

    private static final Color BG = new Color(40, 40, 40);
    private static final Color SIDEBAR = new Color(30, 30, 30);
    private static final Color PANEL = new Color(40, 40, 40);
    private static final Color PANEL_ALT = new Color(30, 30, 30);
    private static final Color INPUT_BG = new Color(30, 30, 30);
    private static final Color BORDER = new Color(23, 23, 23);
    private static final Color TEXT = new Color(198, 198, 198);
    private static final Color MUTED = new Color(165, 165, 165);
    private static final Color ACCENT = new Color(220, 138, 0);
    private static final double PREVIEW_ZOOM_MULTIPLIER = 1.0;
    private static final Path APP_BASE = Paths.get("").toAbsolutePath().normalize();
    private static final Path RECENT_PATHS_FILE = APP_BASE.resolve("recent_paths.dat");
    private static final Path PROJECT_ROOT = APP_BASE.getFileName() != null && APP_BASE.getFileName().toString().equalsIgnoreCase("cache-editor")
            ? APP_BASE.getParent()
            : APP_BASE;

    private final JFrame frame;
    private final JTextField cacheField;
    private final JTextField filterField;
    private final FastListModel scriptListModel;
    private final JList<Object> scriptList;
    private final JTextArea itemDetailsArea;
    private final JTextField itemIdField;
    private final JTextField itemNameField;
    private final JTextField itemPriceField;
    private final JCheckBox itemStackableBox;
    private final JCheckBox itemMembersBox;
    private final JTextField itemModelIdField;
    private final JTextField itemModelZoomField;
    private final JTextField itemRotationXField;
    private final JTextField itemRotationYField;
    private final JTextField itemOffsetXField;
    private final JTextField itemOffsetYField;
    private final JTextField itemEquipSlotField;
    private final JTextField itemEquipTypeField;
    private final JTextField itemMaleEquipField;
    private final JTextField itemFemaleEquipField;
    private final JTextField itemMaleWearOffsetXField;
    private final JTextField itemMaleWearOffsetYField;
    private final JTextField itemMaleWearOffsetZField;
    private final JTextField itemFemaleWearOffsetXField;
    private final JTextField itemFemaleWearOffsetYField;
    private final JTextField itemFemaleWearOffsetZField;
    private final JTextField itemCertField;
    private final JTextField itemLendField;
    private final JTextField itemTeamField;
    private final JTextArea itemGroundOptionsArea;
    private final JTextArea itemInventoryOptionsArea;
    private final JTextArea itemTextureArea;
    private final DefaultListModel<RecolorEntry> itemRecolorListModel;
    private final JList<RecolorEntry> itemRecolorList;
    private final JSlider recolorHueSlider;
    private final JSlider recolorSaturationSlider;
    private final JSlider recolorLightnessSlider;
    private final JTextField recolorPackedValueField;
    private final JPanel recolorOriginalSwatch;
    private final JPanel recolorModifiedSwatch;
    private final JButton recolorSelectColorButton;
    private final JCheckBox recolorShowFacesBox;
    private final DefaultListModel<String> itemParamListModel;
    private final JList<String> itemParamList;
    private final JButton itemParamAddButton;
    private final JButton itemParamRemoveButton;
    private final JButton itemParamApplyTextButton;
    private final JComboBox<ItemPreviewMode> itemPreviewModeCombo;
    private final JSlider itemPreviewZoomSlider;
    private final JSlider itemPreviewRotationXSlider;
    private final JSlider itemPreviewRotationYSlider;
    private final JSlider itemPreviewRotationZSlider;
    private final JSlider itemPreviewOffsetXSlider;
    private final JSlider itemPreviewOffsetYSlider;
    private final JLabel itemPreviewZoomValueLabel;
    private final JLabel itemPreviewRotationXValueLabel;
    private final JLabel itemPreviewRotationYValueLabel;
    private final JLabel itemPreviewRotationZValueLabel;
    private final JLabel itemPreviewOffsetXValueLabel;
    private final JLabel itemPreviewOffsetYValueLabel;
    private final JTextArea logArea;
    private final JLabel statusLabel;
    private final JLabel listTitleLabel;
    private final JLabel listSubtitleLabel;
    private final ItemPreviewPanel itemPreviewPanel;

    private ItemDefinitionsService itemService;
    private ItemModelRenderer itemModelRenderer;
    private List<ItemListEntry> allItems;
    private Integer currentItemId;
    private boolean cacheLoadInProgress;
    private boolean suppressSelectionLoad;
    private boolean suppressPreviewUpdates;
    private boolean suppressRecolorUpdates;
    private Point previewOverlayLocation;
    private Rectangle previewOverlayDragBounds;
    private int selectionRequestId;
    private int filterRequestId;
    private SwingWorker<LoadResult, Void> activeCacheLoadWorker;
    private SwingWorker<ItemSelectionResult, Void> activeItemSelectionWorker;
    private SwingWorker<FilterResult, Void> activeFilterWorker;
    private Map<Integer, Object> currentItemParams;
    private int[] currentOriginalModelColors;
    private int[] currentModifiedModelColors;
    private int[] selectedHighlightedOriginalColors = new int[0];
    private final Timer filterDebounceTimer;
    private final Timer wornPreviewAnimationTimer;
    private final List<String> recentCachePaths;

    public ItemEditorApp() {
        frame = new JFrame("Item Editor");
        cacheField = new JTextField(defaultCachePath());
        filterField = new JTextField();
        scriptListModel = new FastListModel();
        scriptList = new JList<>(scriptListModel);
        itemDetailsArea = createEditorArea();
        itemIdField = createItemField();
        itemNameField = createItemField();
        itemPriceField = createItemField();
        itemStackableBox = createItemCheckBox("Stackable");
        itemMembersBox = createItemCheckBox("Members");
        itemModelIdField = createItemField();
        itemModelZoomField = createItemField();
        itemRotationXField = createItemField();
        itemRotationYField = createItemField();
        itemOffsetXField = createItemField();
        itemOffsetYField = createItemField();
        itemEquipSlotField = createItemField();
        itemEquipTypeField = createItemField();
        itemMaleEquipField = createItemField();
        itemFemaleEquipField = createItemField();
        itemMaleWearOffsetXField = createItemField();
        itemMaleWearOffsetYField = createItemField();
        itemMaleWearOffsetZField = createItemField();
        itemFemaleWearOffsetXField = createItemField();
        itemFemaleWearOffsetYField = createItemField();
        itemFemaleWearOffsetZField = createItemField();
        itemCertField = createItemField();
        itemLendField = createItemField();
        itemTeamField = createItemField();
        itemGroundOptionsArea = createItemBlock();
        itemInventoryOptionsArea = createItemBlock();
        itemTextureArea = createItemBlock();
        itemRecolorListModel = new DefaultListModel<>();
        itemRecolorList = new JList<>(itemRecolorListModel);
        recolorHueSlider = createPreviewSlider(0, 63, 1);
        recolorSaturationSlider = createPreviewSlider(0, 7, 1);
        recolorLightnessSlider = createPreviewSlider(0, 127, 1);
        recolorPackedValueField = createReadOnlyField();
        recolorPackedValueField.setEditable(true);
        recolorPackedValueField.setColumns(6);
        recolorOriginalSwatch = new JPanel();
        recolorModifiedSwatch = new JPanel();
        recolorSelectColorButton = new JButton("Colours");
        recolorShowFacesBox = createItemCheckBox("Show faces");
        itemParamListModel = new DefaultListModel<>();
        itemParamList = new JList<>(itemParamListModel);
        itemParamAddButton = new JButton("Add");
        itemParamRemoveButton = new JButton("Remove");
        itemParamApplyTextButton = new JButton("Apply Text");
        itemPreviewModeCombo = new JComboBox<>(ItemPreviewMode.values());
        itemPreviewZoomSlider = createPreviewSlider(200, 6000, 10);
        itemPreviewRotationXSlider = createPreviewSlider(-1024, 1023, 1);
        itemPreviewRotationYSlider = createPreviewSlider(-1024, 1023, 1);
        itemPreviewRotationZSlider = createPreviewSlider(-1024, 1023, 1);
        itemPreviewOffsetXSlider = createPreviewSlider(-512, 512, 1);
        itemPreviewOffsetYSlider = createPreviewSlider(-512, 512, 1);
        itemPreviewZoomValueLabel = createLabel("", true);
        itemPreviewRotationXValueLabel = createLabel("", true);
        itemPreviewRotationYValueLabel = createLabel("", true);
        itemPreviewRotationZValueLabel = createLabel("", true);
        itemPreviewOffsetXValueLabel = createLabel("", true);
        itemPreviewOffsetYValueLabel = createLabel("", true);
        logArea = createLogArea();
        statusLabel = new JLabel("Cache not loaded");
        listTitleLabel = new JLabel("Items");
        listSubtitleLabel = new JLabel("Definitions");
        itemPreviewPanel = new ItemPreviewPanel();
        allItems = new ArrayList<>();
        currentItemParams = new LinkedHashMap<>();
        currentOriginalModelColors = new int[0];
        currentModifiedModelColors = new int[0];
        previewOverlayLocation = new Point(28, 24);
        previewOverlayDragBounds = new Rectangle(0, 0, 0, 0);
        filterDebounceTimer = new Timer(120, e -> applyFilterNow(false));
        filterDebounceTimer.setRepeats(false);
        wornPreviewAnimationTimer = new Timer(90, e -> {
            if (currentItemId != null && itemPreviewModeCombo.getSelectedItem() != ItemPreviewMode.INVENTORY
                    && itemPreviewPanel.hasRenderedImage() && !itemPreviewPanel.hasRenderFailure()) {
                itemPreviewPanel.queueRender();
            }
        });
        wornPreviewAnimationTimer.setRepeats(true);
        wornPreviewAnimationTimer.start();
        recentCachePaths = new ArrayList<>(loadRecentCachePaths());

        applyLookAndFeelDefaults();
        buildUi();
        wireEvents();
    }

    public static void main(String[] args) {
        try {
            FlatOneDarkIJTheme.setup();
        } catch (Exception ignored) {
        }
        SwingUtilities.invokeLater(() -> new ItemEditorApp().frame.setVisible(true));
    }

    private void applyLookAndFeelDefaults() {
        UIManager.put("Panel.background", PANEL);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("TextField.background", INPUT_BG);
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.caretForeground", TEXT);
        UIManager.put("ComboBox.background", INPUT_BG);
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("List.background", INPUT_BG);
        UIManager.put("List.foreground", TEXT);
        UIManager.put("ScrollPane.border", BorderFactory.createLineBorder(BORDER));
        UIManager.put("OptionPane.background", PANEL);
        UIManager.put("OptionPane.messageForeground", TEXT);
        UIManager.put("Button.arc", 14);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("ScrollBar.width", 12);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.trackArc", 999);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("Button.focusWidth", 0);
        UIManager.put("Button.innerFocusWidth", 0);
        UIManager.put("TitlePane.unifiedBackground", true);
    }

    private void buildUi() {
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(1540, 980);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(BG);

        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBackground(BG);
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        main.add(buildItemToolbar(), BorderLayout.NORTH);
        main.add(buildCenterPanel(), BorderLayout.CENTER);
        main.add(buildBottomPanel(), BorderLayout.SOUTH);

        frame.setContentPane(main);
    }

    private JPanel buildItemToolbar() {
        JPanel panel = createCardPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        JButton recentButton = createButton("Recent", new Color(77, 77, 77));
        JButton browseButton = createButton("Browse", new Color(77, 77, 77));
        JButton loadButton = createButton("Load", ACCENT);

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0;
        panel.add(createLabel("Cache", false), c);

        c.gridx = 1;
        c.weightx = 1;
        panel.add(cacheField, c);

        c.gridx = 2;
        panel.add(recentButton, c);

        c.gridx = 3;
        panel.add(browseButton, c);

        c.gridx = 4;
        panel.add(loadButton, c);

        recentButton.addActionListener(e -> showRecentCacheMenu(recentButton));
        browseButton.addActionListener(e -> chooseDirectory(cacheField));
        loadButton.addActionListener(e -> loadCache());
        return panel;
    }

    private Component buildCenterPanel() {
        JPanel listCard = createCardPanel();
        listCard.setLayout(new BorderLayout(6, 6));
        listCard.add(createDynamicSectionHeader(), BorderLayout.NORTH);
        listCard.add(wrapScroll(scriptList), BorderLayout.CENTER);
        listCard.setPreferredSize(new Dimension(230, 100));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listCard, buildItemWorkspace());
        split.setBorder(null);
        split.setBackground(BG);
        split.setDividerSize(8);
        split.setResizeWeight(0.16);
        split.setDividerLocation(240);
        return split;
    }

    private JPanel buildItemWorkspace() {
        JPanel itemCard = createCardPanel();
        itemCard.setLayout(new BorderLayout(10, 10));
        itemCard.add(buildWorkspaceHeader("Item", "Definition"), BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        installWheelRelay(body);

        JPanel controlsCard = createCardPanel();
        controlsCard.setLayout(new BorderLayout(8, 8));
        controlsCard.add(createSectionHeader("Preview View", "Rotate / move camera"), BorderLayout.NORTH);
        styleCombo(itemPreviewModeCombo);
        itemPreviewModeCombo.addActionListener(e -> {
            ItemPreviewMode previousMode = itemPreviewPanel.mode;
            ItemPreviewMode nextMode = (ItemPreviewMode) itemPreviewModeCombo.getSelectedItem();
            applyPreviewModeDefaults(previousMode, nextMode);
            RecolorEntry selectedRecolor = itemRecolorList.getSelectedValue();
            itemPreviewPanel.setMode(nextMode);
            refreshRecolorList(itemPreviewPanel.item, selectedRecolor == null ? -1 : selectedRecolor.originalColor());
            java.awt.Container parent = itemPreviewPanel.getParent();
            if (parent != null) {
                parent.revalidate();
                parent.repaint();
            }
            itemPreviewPanel.revalidate();
            itemPreviewPanel.repaint();
            itemPreviewPanel.queueRender();
        });
        controlsCard.add(buildPreviewControls(), BorderLayout.CENTER);
        controlsCard.setPreferredSize(new Dimension(520, 260));

        JPanel formCard = buildItemFormCard();
        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, controlsCard, formCard);
        topSplit.setBorder(null);
        topSplit.setBackground(BG);
        topSplit.setDividerSize(8);
        topSplit.setResizeWeight(0.42);
        topSplit.setDividerLocation(520);
        topSplit.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel detailsCard = createCardPanel();
        detailsCard.setLayout(new GridLayout(1, 3, 10, 0));
        detailsCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailsCard.setPreferredSize(new Dimension(100, 348));
        installWheelRelay(detailsCard);
        detailsCard.add(createSplitReadOnlyAreaCard("Actions", "Ground / inventory", "Ground", itemGroundOptionsArea, "Inventory", itemInventoryOptionsArea));
        detailsCard.add(buildAppearanceCard());
        detailsCard.add(buildItemParamsCard());

        body.add(topSplit);
        body.add(Box.createVerticalStrut(10));
        body.add(detailsCard);

        itemPreviewPanel.setPreferredSize(new Dimension(260, 360));
        itemPreviewPanel.setBorder(BorderFactory.createEmptyBorder());
        itemPreviewPanel.setOpaque(false);
        itemPreviewPanel.setOverlayOnly(true);
        final Point[] dragOffset = {null};
        final Point[] panAnchor = {null};
        itemPreviewPanel.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (itemPreviewPanel.beginOverlayInteraction(e)) {
                    dragOffset[0] = null;
                    panAnchor[0] = null;
                    return;
                }
                boolean inside = previewOverlayDragBounds.contains(e.getPoint());
                if (SwingUtilities.isLeftMouseButton(e)) {
                    dragOffset[0] = inside ? e.getPoint() : null;
                    panAnchor[0] = null;
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    panAnchor[0] = inside ? e.getPoint() : null;
                    dragOffset[0] = null;
                }
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                dragOffset[0] = null;
                panAnchor[0] = null;
                itemPreviewPanel.endOverlayInteraction(e.getPoint());
            }
        });
        itemPreviewPanel.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (itemPreviewPanel.handleOverlayDrag(e)) {
                    return;
                }
                if (panAnchor[0] != null && SwingUtilities.isRightMouseButton(e)) {
                    itemPreviewPanel.adjustOverlayPan(e.getX() - panAnchor[0].x, e.getY() - panAnchor[0].y);
                    panAnchor[0] = e.getPoint();
                    return;
                }
                if (dragOffset[0] != null && SwingUtilities.isLeftMouseButton(e)) {
                    java.awt.Container parent = itemPreviewPanel.getParent();
                    if (parent == null) {
                        return;
                    }
                    int newX = itemPreviewPanel.getX() + e.getX() - dragOffset[0].x;
                    int newY = itemPreviewPanel.getY() + e.getY() - dragOffset[0].y;
                    newX = Math.max(8, Math.min(parent.getWidth() - itemPreviewPanel.getWidth() - 8, newX));
                    newY = Math.max(8, Math.min(parent.getHeight() - itemPreviewPanel.getHeight() - 8, newY));
                    previewOverlayLocation = new Point(newX, newY);
                    itemPreviewPanel.setLocation(previewOverlayLocation);
                    parent.repaint();
                    return;
                }
                dragOffset[0] = null;
                panAnchor[0] = null;
            }

            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                itemPreviewPanel.updateOverlayCursor(e.getPoint());
            }
        });
        itemPreviewPanel.addMouseWheelListener(e -> {
            if (itemPreviewPanel.overlayOnly) {
                itemPreviewPanel.adjustOverlayZoom(e.getWheelRotation(), e.getPoint());
                e.consume();
            }
        });

        JScrollPane bodyScroll = wrapScroll(body);
        JLayeredPane layered = new JLayeredPane() {
            @Override
            public void doLayout() {
                int w = getWidth();
                int h = getHeight();
                bodyScroll.setBounds(0, 0, w, h);
                int overlayWidth = itemPreviewModeCombo.getSelectedItem() == ItemPreviewMode.INVENTORY ? 132 : 372;
                int overlayHeight = itemPreviewModeCombo.getSelectedItem() == ItemPreviewMode.INVENTORY ? 132 : 516;
                int x = Math.max(8, Math.min(w - overlayWidth - 8, previewOverlayLocation.x));
                int y = Math.max(8, Math.min(h - overlayHeight - 8, previewOverlayLocation.y));
                previewOverlayLocation = new Point(x, y);
                itemPreviewPanel.setBounds(x, y, overlayWidth, overlayHeight);
            }
        };
        layered.add(bodyScroll, JLayeredPane.DEFAULT_LAYER);
        layered.add(itemPreviewPanel, JLayeredPane.PALETTE_LAYER);
        itemCard.add(layered, BorderLayout.CENTER);
        return itemCard;
    }

    private JPanel buildItemFormCard() {
        JPanel formCard = createCardPanel();
        formCard.setLayout(new BorderLayout(8, 8));
        formCard.add(createSectionHeader("Definition", "Fields"), BorderLayout.NORTH);
        installWheelRelay(formCard);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        installWheelRelay(form);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

            int row = 0;
            row = addFormRow(form, c, row, "Id", itemIdField, "Name", itemNameField);
            row = addFormRow(form, c, row, "Price", itemPriceField, "Team", itemTeamField);

        JPanel flagsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        flagsPanel.setOpaque(false);
        flagsPanel.add(itemStackableBox);
        flagsPanel.add(itemMembersBox);
        row = addFormRow(form, c, row, "Flags", flagsPanel, "Model Id", itemModelIdField);

        row = addFormRow(form, c, row, "Zoom", itemModelZoomField, "Equip Slot", itemEquipSlotField);
        row = addFormRow(form, c, row, "Rotation X", itemRotationXField, "Rotation Y", itemRotationYField);
        row = addFormRow(form, c, row, "Offset X", itemOffsetXField, "Offset Y", itemOffsetYField);
        row = addFormRow(form, c, row, "Equip Type", itemEquipTypeField, "Male Equip", itemMaleEquipField);
        row = addFormRow(form, c, row, "Female Equip", itemFemaleEquipField, "Certificate", itemCertField);
        row = addFormRow(form, c, row, "Male Wear X", createStepperField(itemMaleWearOffsetXField, 1), "Female Wear X", createStepperField(itemFemaleWearOffsetXField, 1));
        row = addFormRow(form, c, row, "Male Wear Y", createStepperField(itemMaleWearOffsetYField, 1), "Female Wear Y", createStepperField(itemFemaleWearOffsetYField, 1));
        row = addFormRow(form, c, row, "Male Wear Z", createStepperField(itemMaleWearOffsetZField, 1), "Female Wear Z", createStepperField(itemFemaleWearOffsetZField, 1));
        addFormRow(form, c, row, "Lend", itemLendField, "Inventory", createReadOnlyAreaCard("Options", "Use / drop", itemInventoryOptionsArea));

            formCard.add(wrapScroll(form), BorderLayout.CENTER);
            return formCard;
        }

    private JPanel buildPreviewControls() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        top.add(createLabel("Mode", true), BorderLayout.WEST);
        top.add(itemPreviewModeCombo, BorderLayout.CENTER);

        JPanel sliders = new JPanel(new GridLayout(6, 1, 0, 6));
        sliders.setOpaque(false);
        sliders.add(buildSliderRow("View Zoom", itemPreviewZoomSlider, itemPreviewZoomValueLabel));
        sliders.add(buildSliderRow("Pitch", itemPreviewRotationXSlider, itemPreviewRotationXValueLabel));
        sliders.add(buildSliderRow("Yaw", itemPreviewRotationYSlider, itemPreviewRotationYValueLabel));
        sliders.add(buildSliderRow("Roll", itemPreviewRotationZSlider, itemPreviewRotationZValueLabel));
        sliders.add(buildSliderRow("Move X", itemPreviewOffsetXSlider, itemPreviewOffsetXValueLabel));
        sliders.add(buildSliderRow("Move Y", itemPreviewOffsetYSlider, itemPreviewOffsetYValueLabel));
        sliders.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(58, 58, 58)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        panel.add(top, BorderLayout.NORTH);
        panel.add(sliders, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSliderRow(String label, JSlider slider, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel text = createLabel(label, true);
        JTextField valueField = createReadOnlyField();
        valueField.setEditable(true);
        valueField.setHorizontalAlignment(JTextField.CENTER);
        valueField.setColumns(5);
        valueField.setPreferredSize(new Dimension(64, 28));
        JButton minusButton = createAdjustButton("-");
        JButton plusButton = createAdjustButton("+");
        minusButton.setPreferredSize(new Dimension(28, 28));
        plusButton.setPreferredSize(new Dimension(28, 28));
        int step = previewStep(slider);
        slider.addChangeListener(e -> {
            valueField.setText(String.valueOf(slider.getValue()));
            if (!suppressPreviewUpdates) {
                pushPreviewOverrides();
            }
        });
        slider.addMouseWheelListener(e -> adjustPreviewSlider(slider, -e.getWheelRotation() * step));
        valueField.addActionListener(e -> applyPreviewFieldValue(slider, valueField));
        valueField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                applyPreviewFieldValue(slider, valueField);
            }
        });
        minusButton.addActionListener(e -> adjustPreviewSlider(slider, -step));
        plusButton.addActionListener(e -> adjustPreviewSlider(slider, step));
        valueField.setText(String.valueOf(slider.getValue()));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        controls.setOpaque(false);
        controls.add(minusButton);
        controls.add(valueField);
        controls.add(plusButton);
        row.add(text, BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        row.add(controls, BorderLayout.EAST);
        return row;
    }

    private int addFormRow(JPanel parent, GridBagConstraints c, int row, String leftLabel, Component leftValue, String rightLabel, Component rightValue) {
        c.gridy = row;
        c.gridx = 0;
        c.weightx = 0;
        parent.add(createLabel(leftLabel, true), c);

        c.gridx = 1;
        c.weightx = 0.5;
        parent.add(leftValue, c);

        c.gridx = 2;
        c.weightx = 0;
        parent.add(createLabel(rightLabel, true), c);

        c.gridx = 3;
        c.weightx = 0.5;
        parent.add(rightValue, c);
        return row + 1;
    }

    private JPanel createReadOnlyAreaCard(String title, String subtitle, JTextArea area) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);
        panel.add(createSectionHeader(title, subtitle), BorderLayout.NORTH);
        JScrollPane scrollPane = wrapScroll(area);
        scrollPane.setPreferredSize(new Dimension(100, 112));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createSplitReadOnlyAreaCard(String title, String subtitle, String topTitle, JTextArea topArea, String bottomTitle, JTextArea bottomArea) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);
        panel.add(createSectionHeader(title, subtitle), BorderLayout.NORTH);

        JPanel body = new JPanel(new GridLayout(2, 1, 8, 8));
        body.setOpaque(false);
        body.add(createReadOnlyAreaCard(topTitle, "", topArea));
        body.add(createReadOnlyAreaCard(bottomTitle, "", bottomArea));
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildAppearanceCard() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);
        panel.add(createSectionHeader("Appearance", "Recolors / textures"), BorderLayout.NORTH);

        itemRecolorList.setBackground(INPUT_BG);
        itemRecolorList.setForeground(TEXT);
        itemRecolorList.setSelectionBackground(ACCENT);
        itemRecolorList.setSelectionForeground(Color.WHITE);
        itemRecolorList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        installConditionalLocalWheelRelay(itemRecolorList);

        recolorOriginalSwatch.setPreferredSize(new Dimension(36, 24));
        recolorModifiedSwatch.setPreferredSize(new Dimension(36, 24));
        recolorOriginalSwatch.setBorder(BorderFactory.createLineBorder(BORDER));
        recolorModifiedSwatch.setBorder(BorderFactory.createLineBorder(BORDER));
        styleButton(recolorSelectColorButton, new Color(77, 77, 77));
        recolorSelectColorButton.setPreferredSize(new Dimension(84, 26));
        recolorSelectColorButton.addActionListener(e -> openRecolorPicker());
        recolorShowFacesBox.setSelected(true);
        recolorShowFacesBox.addActionListener(e -> {
            selectedHighlightedOriginalColors = highlightedOriginalColorsFromSelection();
            itemPreviewPanel.invalidateRenderKey();
            itemPreviewPanel.queueRenderNow();
        });
        recolorPackedValueField.addActionListener(e -> applyPackedRecolorField());
        recolorPackedValueField.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                applyPackedRecolorField();
            }
        });

        JPanel picker = new JPanel(new BorderLayout(6, 6));
        picker.setOpaque(false);
        JPanel swatches = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        swatches.setOpaque(false);
        swatches.add(createLabel("Orig", true));
        swatches.add(recolorOriginalSwatch);
        swatches.add(createLabel("New", true));
        swatches.add(recolorModifiedSwatch);
        swatches.add(createLabel("Id", true));
        swatches.add(recolorPackedValueField);
        swatches.add(recolorSelectColorButton);
        JPanel optionsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        optionsRow.setOpaque(false);
        optionsRow.add(recolorShowFacesBox);

        JPanel sliders = new JPanel(new GridLayout(3, 1, 0, 4));
        sliders.setOpaque(false);
        sliders.add(buildSimpleSliderRow("Hue", recolorHueSlider));
        sliders.add(buildSimpleSliderRow("Sat", recolorSaturationSlider));
        sliders.add(buildSimpleSliderRow("Light", recolorLightnessSlider));
        picker.add(swatches, BorderLayout.NORTH);
        picker.add(sliders, BorderLayout.CENTER);
        picker.add(optionsRow, BorderLayout.SOUTH);

        JSplitPane top = new JSplitPane(JSplitPane.VERTICAL_SPLIT, wrapScroll(itemRecolorList), picker);
        top.setBorder(null);
        top.setBackground(PANEL);
        top.setDividerSize(8);
        top.setResizeWeight(0.58);
        top.setDividerLocation(164);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, wrapScroll(itemTextureArea));
        split.setBorder(null);
        split.setBackground(PANEL);
        split.setDividerSize(8);
        split.setResizeWeight(0.72);
        split.setDividerLocation(278);

        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSimpleSliderRow(String label, JSlider slider) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(createLabel(label, true), BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        return row;
    }

    private JPanel buildItemParamsCard() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setOpaque(false);
        panel.add(createSectionHeader("Params", "Structured + text"), BorderLayout.NORTH);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        toolbar.setOpaque(false);
        styleButton(itemParamAddButton, ACCENT);
        styleButton(itemParamRemoveButton, new Color(77, 77, 77));
        styleButton(itemParamApplyTextButton, new Color(77, 77, 77));
        toolbar.add(itemParamAddButton);
        toolbar.add(itemParamRemoveButton);
        toolbar.add(itemParamApplyTextButton);

        itemParamList.setBackground(INPUT_BG);
        itemParamList.setForeground(TEXT);
        itemParamList.setSelectionBackground(ACCENT);
        itemParamList.setSelectionForeground(Color.WHITE);
        installWheelRelay(itemParamList);
        installWheelRelay(itemDetailsArea);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, wrapScroll(itemParamList, true), wrapScroll(itemDetailsArea, true));
        split.setBorder(null);
        split.setBackground(PANEL);
        split.setDividerSize(8);
        split.setResizeWeight(0.48);
        split.setDividerLocation(176);

        JPanel body = new JPanel(new BorderLayout(8, 8));
        body.setOpaque(false);
        body.add(toolbar, BorderLayout.NORTH);
        body.add(split, BorderLayout.CENTER);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createDynamicSectionHeader() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BorderLayout(0, 8));

        JPanel labels = new JPanel();
        labels.setOpaque(false);
        labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));

        listTitleLabel.setForeground(TEXT);
        listTitleLabel.setFont(listTitleLabel.getFont().deriveFont(Font.BOLD, 13f));

        listSubtitleLabel.setForeground(MUTED);
        listSubtitleLabel.setFont(listSubtitleLabel.getFont().deriveFont(Font.PLAIN, 11f));

        labels.add(listTitleLabel);
        labels.add(listSubtitleLabel);

        JPanel searchPanel = new JPanel(new BorderLayout(0, 4));
        searchPanel.setOpaque(false);
        searchPanel.add(createLabel("Search", true), BorderLayout.NORTH);
        searchPanel.add(filterField, BorderLayout.CENTER);

        panel.add(labels, BorderLayout.NORTH);
        panel.add(searchPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildWorkspaceHeader(String title, String subtitle) {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PANEL_ALT);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                BorderFactory.createEmptyBorder(9, 12, 9, 12)
        ));
        header.add(createSectionHeader(title, subtitle), BorderLayout.WEST);
        styleLabel(statusLabel, true);
        header.add(statusLabel, BorderLayout.EAST);
        return header;
    }

    private JPanel buildBottomPanel() {
        JPanel container = new JPanel(new BorderLayout(8, 8));
        container.setBackground(BG);

        container.add(wrapScroll(logArea), BorderLayout.CENTER);
        return container;
    }

    private void wireEvents() {
        styleField(cacheField);
        styleField(filterField);

        scriptList.setBackground(INPUT_BG);
        scriptList.setForeground(TEXT);
        scriptList.setSelectionBackground(ACCENT);
        scriptList.setSelectionForeground(Color.WHITE);
        scriptList.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        scriptList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, isSelected ? ACCENT : BG),
                        BorderFactory.createEmptyBorder(8, 10, 8, 10)
                ));
                label.setBackground(isSelected ? ACCENT : INPUT_BG);
                label.setForeground(isSelected ? Color.WHITE : TEXT);
                return label;
            }
        });

        scriptList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !suppressSelectionLoad) {
                loadSelectedItem();
            }
        });

        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                queueFilter(false);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                queueFilter(false);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                queueFilter(false);
            }
        });

        itemParamAddButton.addActionListener(e -> addItemParam());
        itemParamRemoveButton.addActionListener(e -> removeSelectedItemParam());
        itemParamApplyTextButton.addActionListener(e -> applyItemParamText());
        itemRecolorList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                refreshRecolorEditor();
                selectedHighlightedOriginalColors = highlightedOriginalColorsFromSelection();
                itemPreviewPanel.invalidateRenderKey();
                itemPreviewPanel.queueRenderNow();
            }
        });
        final int[] lastClickedRecolorIndex = {-1};
        itemRecolorList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int index = itemRecolorList.locationToIndex(e.getPoint());
                Rectangle bounds = index >= 0 ? itemRecolorList.getCellBounds(index, index) : null;
                if (bounds == null || !bounds.contains(e.getPoint())) {
                    lastClickedRecolorIndex[0] = -1;
                    return;
                }
                if (itemRecolorList.isSelectedIndex(index) && lastClickedRecolorIndex[0] == index) {
                    SwingUtilities.invokeLater(itemRecolorList::clearSelection);
                    lastClickedRecolorIndex[0] = -1;
                    return;
                }
                lastClickedRecolorIndex[0] = index;
            }
        });
        javax.swing.event.ChangeListener recolorListener = e -> applySelectedRecolor();
        recolorHueSlider.addChangeListener(recolorListener);
        recolorSaturationSlider.addChangeListener(recolorListener);
        recolorLightnessSlider.addChangeListener(recolorListener);

        DocumentListener wearOffsetPreviewListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                if (!suppressPreviewUpdates) {
                    itemPreviewPanel.queueRenderNow();
                }
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                if (!suppressPreviewUpdates) {
                    itemPreviewPanel.queueRenderNow();
                }
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                if (!suppressPreviewUpdates) {
                    itemPreviewPanel.queueRenderNow();
                }
            }
        };
        itemMaleWearOffsetXField.getDocument().addDocumentListener(wearOffsetPreviewListener);
        itemMaleWearOffsetYField.getDocument().addDocumentListener(wearOffsetPreviewListener);
        itemMaleWearOffsetZField.getDocument().addDocumentListener(wearOffsetPreviewListener);
        itemFemaleWearOffsetXField.getDocument().addDocumentListener(wearOffsetPreviewListener);
        itemFemaleWearOffsetYField.getDocument().addDocumentListener(wearOffsetPreviewListener);
        itemFemaleWearOffsetZField.getDocument().addDocumentListener(wearOffsetPreviewListener);
    }

    private void loadCache() {
        if (cacheLoadInProgress || (activeCacheLoadWorker != null && !activeCacheLoadWorker.isDone())) {
            return;
        }
        cancelActiveFilterWorker();
        if (activeItemSelectionWorker != null && !activeItemSelectionWorker.isDone()) {
            activeItemSelectionWorker.cancel(true);
        }
        itemPreviewPanel.clear();
        setCacheLoadInProgress(true, "Loading cache...");
        SwingWorker<LoadResult, Void> worker = new SwingWorker<>() {
            @Override
            protected LoadResult doInBackground() {
                try {
                    Path cachePath = resolveUserPath(cacheField.getText().trim());
                    ItemDefinitionsService loadedItems = new ItemDefinitionsService(cachePath);
                    return LoadResult.success(loadedItems);
                } catch (Exception e) {
                    return LoadResult.failure(e);
                }
            }

            @Override
            protected void done() {
                finishLoad(this);
            }
        };
        activeCacheLoadWorker = worker;
        worker.execute();
    }

    private void finishLoad(SwingWorker<LoadResult, Void> worker) {
        if (worker != activeCacheLoadWorker) {
            return;
        }
        try {
            LoadResult result = worker.get();
            if (result.error() != null) {
                appendLog("Failed to load cache: " + result.error().getMessage());
                JOptionPane.showMessageDialog(frame, result.error().getMessage(), "Cache Load Failed", JOptionPane.ERROR_MESSAGE);
            } else {
                itemService = result.itemService();
                rememberRecentCachePath(itemService.getCachePath().toString());
                itemModelRenderer = itemService == null ? null : new ItemModelRenderer(itemService);
                itemPreviewPanel.setRenderer(itemModelRenderer);
                appendLog("Loaded cache: " + itemService.getCachePath());
                rebuildCurrentList();
                refreshCurrentSelection();
            }
        } catch (Exception e) {
            String details = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
            appendLog("Failed to finish cache load: " + details);
            JOptionPane.showMessageDialog(frame, details, "Cache Load Failed", JOptionPane.ERROR_MESSAGE);
        } finally {
            activeCacheLoadWorker = null;
            setCacheLoadInProgress(false, "");
        }
    }

    private void rebuildCurrentList() {
        allItems = itemService == null ? new ArrayList<>() : itemService.listItems();
        scriptListModel.setItems(List.of());
        applyFilterNow(true);
    }

    private void queueFilter(boolean autoSelectIfEmpty) {
        cancelActiveFilterWorker();
        if (autoSelectIfEmpty) {
            filterDebounceTimer.stop();
            applyFilterNow(true);
            return;
        }
        filterDebounceTimer.restart();
    }

    private void applyFilterNow(boolean autoSelectIfEmpty) {
        filterDebounceTimer.stop();
        cancelActiveFilterWorker();
        String filter = filterField.getText().trim();
        Object selected = scriptList.getSelectedValue();
        Object preferred = findCurrentItemEntry();
        List<ItemListEntry> itemEntriesSnapshot = new ArrayList<>(allItems);
        int requestId = ++filterRequestId;
        activeFilterWorker = new SwingWorker<>() {
            @Override
            protected FilterResult doInBackground() {
                List<Object> results = new ArrayList<>();
                for (ItemListEntry entry : itemEntriesSnapshot) {
                    if (isCancelled()) {
                        return null;
                    }
                    if (entry.matches(filter)) {
                        results.add(entry);
                    }
                }
                return new FilterResult(requestId, results, preferred, selected, autoSelectIfEmpty);
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    FilterResult result = get();
                    if (result == null || result.requestId() != filterRequestId) {
                        return;
                    }
                    suppressSelectionLoad = true;
                    scriptListModel.setItems(result.results());
                    if (result.preferred() != null && scriptListModel.contains(result.preferred())) {
                        scriptList.setSelectedValue(result.preferred(), true);
                    } else if (result.selected() != null && scriptListModel.contains(result.selected())) {
                        scriptList.setSelectedValue(result.selected(), true);
                    } else if (result.autoSelectIfEmpty() && !scriptListModel.isEmpty()) {
                        scriptList.setSelectedIndex(0);
                    } else {
                        scriptList.clearSelection();
                    }
                } catch (Exception e) {
                    appendLog("Filter update failed: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
                } finally {
                    suppressSelectionLoad = false;
                }
            }
        };
        activeFilterWorker.execute();
    }

    private void cancelActiveFilterWorker() {
        if (activeFilterWorker != null && !activeFilterWorker.isDone()) {
            activeFilterWorker.cancel(true);
        }
        activeFilterWorker = null;
    }

    private void loadSelectedItem() {
        if (activeItemSelectionWorker != null && !activeItemSelectionWorker.isDone()) {
            activeItemSelectionWorker.cancel(true);
        }
        Object selectedValue = scriptList.getSelectedValue();
        ItemListEntry selected = selectedValue instanceof ItemListEntry entry ? entry : null;
        currentItemId = selected == null ? null : selected.id();
        if (selected == null || itemService == null) {
            clearItemDetails();
            itemPreviewPanel.clear();
            statusLabel.setText("Cache not loaded");
            return;
        }

        int requestId = ++selectionRequestId;
        appendLog("[item " + selected.id() + "] selection start");
        clearItemDetails();
        itemDetailsArea.setText("// loading item " + selected.id() + "...\n");
        itemPreviewPanel.clear();
        statusLabel.setText("Loading item " + selected.id() + "...");

        SwingWorker<ItemSelectionResult, Void> worker = new SwingWorker<>() {
            @Override
            protected ItemSelectionResult doInBackground() {
                if (isCancelled()) {
                    return null;
                }
                long startedAt = System.nanoTime();
                appendLog("[item " + selected.id() + "] worker load start");
                appendLog(itemService.debugDescribeItemLoad(selected.id()));
                ItemDefinitionRecord item = itemService.load(selected.id());
                appendLog("[item " + selected.id() + "] worker load done model=" + item.modelId()
                        + " zoom=" + item.modelZoom()
                        + " rot=" + item.modelRotation1() + "/" + item.modelRotation2() + "/" + item.modelRotation3()
                        + " off=" + item.modelOffset1() + "/" + item.modelOffset2()
                        + " scale=" + item.modelScaleX() + "/" + item.modelScaleY() + "/" + item.modelScaleZ()
                        + " note=" + item.certId() + "/" + item.certTemplateId()
                        + " lend=" + item.lendId() + "/" + item.lendTemplateId()
                        + " ms=" + ((System.nanoTime() - startedAt) / 1_000_000.0));
                String info = itemService.getCachePath() + " | item " + selected.id() + " | " + item.name();
                return new ItemSelectionResult(requestId, item, info);
            }

            @Override
            protected void done() {
                try {
                    if (isCancelled()) {
                        return;
                    }
                    ItemSelectionResult result = get();
                    if (result == null) {
                        return;
                    }
                    if (result.requestId() != selectionRequestId) {
                        return;
                    }
                    appendLog("[item " + selected.id() + "] populate start");
                    populateItemDetails(result.item());
                    appendLog("[item " + selected.id() + "] populate done");
                    armEdtWatchdog("item " + selected.id() + " post-populate", 1500);
                    statusLabel.setText(result.info());
                } catch (Exception e) {
                    if (requestId != selectionRequestId) {
                        return;
                    }
                    appendLog("[item " + selected.id() + "] worker load fail " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
                    clearItemDetails();
                    itemDetailsArea.setText("// failed to load item " + selected.id() + ": " + e.getMessage() + "\n");
                    itemPreviewPanel.clear();
                    statusLabel.setText("Item load failed");
                }
            }
        };
        activeItemSelectionWorker = worker;
        worker.execute();
    }

    private ItemListEntry findCurrentItemEntry() {
        if (currentItemId == null) {
            return null;
        }
        for (ItemListEntry entry : allItems) {
            if (entry.id() == currentItemId) {
                return entry;
            }
        }
        return null;
    }

    private void refreshCurrentSelection() {
        if (currentItemId != null) {
            loadSelectedItem();
        }
    }

    private void setCacheLoadInProgress(boolean loading, String statusMessage) {
        cacheLoadInProgress = loading;
        cacheField.setEnabled(!loading);
        filterField.setEnabled(!loading);
        scriptList.setEnabled(!loading);
        if (loading) {
            statusLabel.setText(statusMessage);
            appendLog(statusMessage);
        }
    }

    private void appendLog(String message) {
        System.out.println(message);
        Runnable uiAppend = () -> {
            if (!logArea.getText().isEmpty()) {
                logArea.append(System.lineSeparator());
            }
            logArea.append(message);
            logArea.setCaretPosition(logArea.getDocument().getLength());
        };
        if (SwingUtilities.isEventDispatchThread()) {
            uiAppend.run();
        } else {
            SwingUtilities.invokeLater(uiAppend);
        }
    }

    private void armEdtWatchdog(String label, long timeoutMs) {
        AtomicBoolean completed = new AtomicBoolean(false);
        SwingUtilities.invokeLater(() -> {
            completed.set(true);
            appendLog("[watchdog] EDT responsive after " + label);
        });
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(timeoutMs);
            } catch (InterruptedException ignored) {
                return;
            }
            if (completed.get()) {
                return;
            }
            appendLog("[watchdog] EDT stall detected after " + label + " timeoutMs=" + timeoutMs);
            for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                Thread thread = entry.getKey();
                if (!thread.getName().startsWith("AWT-EventQueue")) {
                    continue;
                }
                appendLog("[watchdog] EDT thread=" + thread.getName() + " state=" + thread.getState());
                for (StackTraceElement element : entry.getValue()) {
                    appendLog("[watchdog]   at " + element);
                }
            }
        }, "edt-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void chooseDirectory(JTextField targetField) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (!targetField.getText().isBlank()) {
            chooser.setCurrentDirectory(resolveUserPath(targetField.getText()).toFile());
        }
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            targetField.setText(path);
            rememberRecentCachePath(path);
        }
    }

    private List<String> loadRecentCachePaths() {
        if (!Files.exists(RECENT_PATHS_FILE)) {
            return List.of();
        }
        try {
            return Files.readAllLines(RECENT_PATHS_FILE).stream()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .distinct()
                    .toList();
        } catch (IOException ignored) {
            return List.of();
        }
    }

    private void rememberRecentCachePath(String path) {
        String normalized = path == null ? "" : path.trim();
        if (normalized.isEmpty()) {
            return;
        }
        recentCachePaths.removeIf(existing -> existing.equalsIgnoreCase(normalized));
        recentCachePaths.add(0, normalized);
        while (recentCachePaths.size() > 12) {
            recentCachePaths.remove(recentCachePaths.size() - 1);
        }
        try {
            Files.write(RECENT_PATHS_FILE, recentCachePaths);
        } catch (IOException ignored) {
        }
    }

    private void showRecentCacheMenu(Component anchor) {
        JPopupMenu menu = new JPopupMenu();
        if (recentCachePaths.isEmpty()) {
            JMenuItem empty = new JMenuItem("No recent caches");
            empty.setEnabled(false);
            menu.add(empty);
        } else {
            for (String path : recentCachePaths) {
                JMenuItem item = new JMenuItem(path);
                item.addActionListener(e -> cacheField.setText(path));
                menu.add(item);
            }
        }
        menu.show(anchor, 0, anchor.getHeight());
    }

    private JPanel createCardPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));
        panel.putClientProperty(FlatClientProperties.STYLE, "arc:18");
        return panel;
    }

    private JLabel createLabel(String text, boolean muted) {
        JLabel label = new JLabel(text);
        styleLabel(label, muted);
        return label;
    }

    private JPanel createSectionHeader(String title, String subtitle) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(TEXT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));

        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setForeground(MUTED);
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 11f));

        panel.add(titleLabel);
        panel.add(subtitleLabel);
        return panel;
    }

    private JLabel createPill(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setOpaque(true);
        label.setBackground(color);
        label.setForeground(Color.WHITE);
        label.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        return label;
    }

    private JPanel createSidebarSection(String title, String body) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(SIDEBAR);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(TEXT);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));

        JTextArea bodyArea = new JTextArea(body);
        bodyArea.setEditable(false);
        bodyArea.setWrapStyleWord(true);
        bodyArea.setLineWrap(true);
        bodyArea.setOpaque(false);
        bodyArea.setForeground(MUTED);
        bodyArea.setFont(bodyArea.getFont().deriveFont(Font.PLAIN, 12f));

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(bodyArea, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createMetricCard(String label, String value) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(SIDEBAR);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JLabel top = new JLabel(label);
        top.setForeground(MUTED);
        top.setFont(top.getFont().deriveFont(Font.PLAIN, 11f));

        JLabel bottom = new JLabel(value);
        bottom.setForeground(TEXT);
        bottom.setFont(bottom.getFont().deriveFont(Font.BOLD, 13f));

        panel.add(top, BorderLayout.NORTH);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private JButton createButton(String text, Color bg) {
        JButton button = new JButton(text);
        styleButton(button, bg);
        return button;
    }

    private void styleButton(JButton button, Color bg) {
        button.setFocusPainted(false);
        button.setBorderPainted(true);
        button.setOpaque(true);
        button.setBackground(bg);
        button.setForeground(Color.WHITE);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 12.5f));
        button.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty(FlatClientProperties.STYLE,
                "arc:14;" +
                "focusWidth:0;" +
                "innerFocusWidth:0;" +
                "borderWidth:1;" +
                "background:" + toHex(bg) + ";" +
                "foreground:#FFFFFF");
    }

    private void styleLabel(JLabel label, boolean muted) {
        label.setForeground(muted ? MUTED : TEXT);
    }

    private void styleField(JTextField field) {
        field.setBackground(INPUT_BG);
        field.setForeground(TEXT);
        field.setCaretColor(TEXT);
        field.setBorder(createInputBorder());
        field.putClientProperty(FlatClientProperties.STYLE,
                "arc:12;" +
                "focusWidth:1;" +
                "innerFocusWidth:0;" +
                "borderWidth:1;" +
                "margin:6,8,6,8");
    }

    private void styleCombo(JComboBox<?> combo) {
        combo.setBackground(INPUT_BG);
        combo.setForeground(TEXT);
        combo.setBorder(createInputBorder());
        combo.putClientProperty(FlatClientProperties.STYLE,
                "focusWidth:1;" +
                "innerFocusWidth:0;" +
                "borderWidth:1");
    }

    private Border createInputBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        );
    }

    private static String toHex(Color color) {
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    private JScrollPane wrapScroll(Component component) {
        return wrapScroll(component, false);
    }

    private JScrollPane wrapScroll(Component component, boolean localWheelScroll) {
        JScrollPane pane = new JScrollPane(component);
        pane.setBorder(BorderFactory.createLineBorder(BORDER));
        pane.getViewport().setBackground(INPUT_BG);
        pane.setWheelScrollingEnabled(true);
        if (localWheelScroll) {
            installWheelRelay(pane.getViewport(), true);
        }
        pane.getVerticalScrollBar().setUnitIncrement(24);
        pane.getVerticalScrollBar().setBlockIncrement(96);
        pane.getHorizontalScrollBar().setUnitIncrement(14);
        return pane;
    }

    private JTextArea createEditorArea() {
        JTextArea area = new JTextArea();
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        area.setTabSize(4);
        area.setLineWrap(false);
        area.setBackground(INPUT_BG);
        area.setForeground(TEXT);
        area.setCaretColor(TEXT);
        area.setSelectionColor(new Color(220, 138, 0, 120));
        area.setSelectedTextColor(Color.WHITE);
        area.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        area.putClientProperty(FlatClientProperties.STYLE,
                "arc:12;" +
                "focusWidth:1;" +
                "innerFocusWidth:0;" +
                "margin:8,10,8,10");
        return area;
    }

    private JSlider createPreviewSlider(int min, int max, int step) {
        JSlider slider = new JSlider(min, max);
        slider.setOpaque(false);
        slider.setBackground(PANEL);
        slider.setForeground(TEXT);
        slider.setFocusable(false);
        slider.putClientProperty("previewStep", step);
        return slider;
    }

    private JButton createAdjustButton(String text) {
        JButton button = new JButton(text);
        button.setFocusable(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFont(button.getFont().deriveFont(Font.BOLD, 14f));
        button.putClientProperty(FlatClientProperties.STYLE,
                "arc:10;" +
                "focusWidth:0;" +
                "innerFocusWidth:0;" +
                "borderWidth:1;" +
                "minimumWidth:28;" +
                "minimumHeight:28;" +
                "background:#4D4D4D;" +
                "foreground:#FFFFFF");
        return button;
    }

    private int previewStep(JSlider slider) {
        Object value = slider.getClientProperty("previewStep");
        return value instanceof Integer integer && integer > 0 ? integer : 1;
    }

    private void adjustPreviewSlider(JSlider slider, int delta) {
        int value = Math.max(slider.getMinimum(), Math.min(slider.getMaximum(), slider.getValue() + delta));
        slider.setValue(value);
    }

    private void applyPreviewFieldValue(JSlider slider, JTextField field) {
        String text = field.getText().trim();
        if (text.isEmpty()) {
            field.setText(String.valueOf(slider.getValue()));
            return;
        }
        try {
            int parsed = Integer.parseInt(text);
            parsed = Math.max(slider.getMinimum(), Math.min(slider.getMaximum(), parsed));
            slider.setValue(parsed);
            field.setText(String.valueOf(parsed));
        } catch (NumberFormatException ignored) {
            field.setText(String.valueOf(slider.getValue()));
        }
    }

    private JTextField createReadOnlyField() {
        JTextField field = new JTextField();
        field.setEditable(false);
        field.setBackground(INPUT_BG);
        field.setForeground(TEXT);
        field.setCaretColor(TEXT);
        field.setBorder(createInputBorder());
        field.putClientProperty(FlatClientProperties.STYLE,
                "arc:12;" +
                "focusWidth:0;" +
                "innerFocusWidth:0;" +
                "borderWidth:1");
        return field;
    }

    private JTextField createItemField() {
        JTextField field = new JTextField();
        field.setEditable(true);
        field.setBackground(INPUT_BG);
        field.setForeground(TEXT);
        field.setCaretColor(TEXT);
        field.setBorder(createInputBorder());
        field.putClientProperty(FlatClientProperties.STYLE,
                "arc:12;" +
                "focusWidth:1;" +
                "innerFocusWidth:0;" +
                "borderWidth:1;" +
                "margin:6,8,6,8");
        installWheelRelay(field, false);
        return field;
    }

    private JComponent createStepperField(JTextField field, int step) {
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        panel.setOpaque(false);
        JButton minusButton = createAdjustButton("-");
        JButton plusButton = createAdjustButton("+");
        minusButton.setPreferredSize(new Dimension(28, 28));
        plusButton.setPreferredSize(new Dimension(28, 28));
        minusButton.addActionListener(e -> adjustIntegerField(field, -step));
        plusButton.addActionListener(e -> adjustIntegerField(field, step));
        panel.add(minusButton, BorderLayout.WEST);
        panel.add(field, BorderLayout.CENTER);
        panel.add(plusButton, BorderLayout.EAST);
        return panel;
    }

    private void adjustIntegerField(JTextField field, int delta) {
        int value = parseIntOrDefault(field.getText(), 0) + delta;
        field.setText(String.valueOf(value));
    }

    private JCheckBox createReadOnlyCheckBox(String text) {
        JCheckBox box = new JCheckBox(text);
        box.setEnabled(false);
        box.setOpaque(false);
        box.setForeground(TEXT);
        return box;
    }

    private JCheckBox createItemCheckBox(String text) {
        JCheckBox box = new JCheckBox(text);
        box.setEnabled(true);
        box.setOpaque(false);
        box.setForeground(TEXT);
        installWheelRelay(box, false);
        return box;
    }

    private JTextArea createReadOnlyBlock() {
        JTextArea area = createEditorArea();
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private JTextArea createItemBlock() {
        JTextArea area = createEditorArea();
        area.setEditable(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setRows(4);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.putClientProperty(FlatClientProperties.STYLE,
                "arc:12;" +
                "focusWidth:1;" +
                "innerFocusWidth:0;" +
                "margin:8,10,8,10");
        installWheelRelay(area, false);
        return area;
    }

    private void installWheelRelay(Component component) {
        installWheelRelay(component, false);
    }

    private void installConditionalLocalWheelRelay(Component component) {
        component.addMouseWheelListener(event -> {
            JScrollPane nearest = null;
            JScrollPane outermost = null;
            Component cursor = component;
            while (cursor != null) {
                if (cursor instanceof JScrollPane scrollPane) {
                    if (nearest == null) {
                        nearest = scrollPane;
                    }
                    outermost = scrollPane;
                }
                cursor = cursor.getParent();
            }
            JScrollPane target = nearest;
            if (nearest != null) {
                JScrollBar bar = nearest.getVerticalScrollBar();
                boolean scrollable = bar != null && bar.isVisible() && bar.getMaximum() > bar.getVisibleAmount();
                if (!scrollable) {
                    target = outermost;
                }
            }
            if (target == null) {
                return;
            }
            Point point = SwingUtilities.convertPoint(component, event.getPoint(), target);
            MouseWheelEvent forwarded = new MouseWheelEvent(
                    target,
                    event.getID(),
                    event.getWhen(),
                    event.getModifiersEx(),
                    point.x,
                    point.y,
                    event.getXOnScreen(),
                    event.getYOnScreen(),
                    event.getClickCount(),
                    event.isPopupTrigger(),
                    event.getScrollType(),
                    event.getScrollAmount(),
                    event.getWheelRotation(),
                    event.getPreciseWheelRotation()
            );
            target.dispatchEvent(forwarded);
        });
    }

    private void installWheelRelay(Component component, boolean nearestScrollPane) {
        component.addMouseWheelListener(event -> {
            JScrollPane target = null;
            Component cursor = component;
            while (cursor != null) {
                if (cursor instanceof JScrollPane scrollPane) {
                    target = scrollPane;
                    if (nearestScrollPane) {
                        break;
                    }
                }
                cursor = cursor.getParent();
            }
            if (target == null) {
                return;
            }
            Point point = SwingUtilities.convertPoint(component, event.getPoint(), target);
            MouseWheelEvent forwarded = new MouseWheelEvent(
                    target,
                    event.getID(),
                    event.getWhen(),
                    event.getModifiersEx(),
                    point.x,
                    point.y,
                    event.getXOnScreen(),
                    event.getYOnScreen(),
                    event.getClickCount(),
                    event.isPopupTrigger(),
                    event.getScrollType(),
                    event.getScrollAmount(),
                    event.getWheelRotation(),
                    event.getPreciseWheelRotation()
            );
            target.dispatchEvent(forwarded);
        });
    }

    private void populateItemDetails(ItemDefinitionRecord item) {
        itemIdField.setText(String.valueOf(item.id()));
        itemNameField.setText(item.name());
        itemPriceField.setText(String.valueOf(item.price()));
        itemStackableBox.setSelected(item.stackable());
        itemMembersBox.setSelected(item.membersOnly());
        itemModelIdField.setText(String.valueOf(item.modelId()));
        itemModelZoomField.setText(String.valueOf(item.modelZoom()));
        itemRotationXField.setText(String.valueOf(item.modelRotation1()));
        itemRotationYField.setText(String.valueOf(item.modelRotation2()));
        itemOffsetXField.setText(String.valueOf(item.modelOffset1()));
        itemOffsetYField.setText(String.valueOf(item.modelOffset2()));
        itemEquipSlotField.setText(String.valueOf(item.equipSlot()));
        itemEquipTypeField.setText(String.valueOf(item.equipType()));
        itemMaleEquipField.setText(joinInts(item.maleEquip1(), item.maleEquip2()));
        itemFemaleEquipField.setText(joinInts(item.femaleEquip1(), item.femaleEquip2()));
        itemMaleWearOffsetXField.setText(String.valueOf(item.maleWearOffsetX()));
        itemMaleWearOffsetYField.setText(String.valueOf(item.maleWearOffsetY()));
        itemMaleWearOffsetZField.setText(String.valueOf(item.maleWearOffsetZ()));
        itemFemaleWearOffsetXField.setText(String.valueOf(item.femaleWearOffsetX()));
        itemFemaleWearOffsetYField.setText(String.valueOf(item.femaleWearOffsetY()));
        itemFemaleWearOffsetZField.setText(String.valueOf(item.femaleWearOffsetZ()));
        itemCertField.setText(joinInts(item.certId(), item.certTemplateId()));
        itemLendField.setText(joinInts(item.lendId(), item.lendTemplateId()));
        itemTeamField.setText(String.valueOf(item.teamId()));
        itemGroundOptionsArea.setText(joinLines(item.groundOptions()));
        itemInventoryOptionsArea.setText(joinLines(item.inventoryOptions()));
        currentItemParams = new LinkedHashMap<>(new TreeMap<>(item.clientScriptData()));
        currentOriginalModelColors = new int[0];
        currentModifiedModelColors = new int[0];
        selectedHighlightedOriginalColors = new int[0];
        itemTextureArea.setText(joinPairs(item.originalTextureColors(), item.modifiedTextureColors()));
        refreshRecolorList(item, -1);
        refreshItemParamsView();
        itemPreviewPanel.setItemSilently(item);
        suppressPreviewUpdates = true;
        applyPreviewDefaultsForMode((ItemPreviewMode) itemPreviewModeCombo.getSelectedItem(), item);
        suppressPreviewUpdates = false;
        appendLog("[item " + item.id() + "] preview kickoff queued");
        SwingUtilities.invokeLater(() -> {
            if (currentItemId != null && currentItemId == item.id()) {
                appendLog("[item " + item.id() + "] preview kickoff run");
                pushPreviewOverrides();
            } else {
                appendLog("[item " + item.id() + "] preview kickoff skipped current=" + currentItemId);
            }
        });
        itemGroundOptionsArea.setCaretPosition(0);
        itemInventoryOptionsArea.setCaretPosition(0);
        itemTextureArea.setCaretPosition(0);
    }

    private void clearItemDetails() {
        itemIdField.setText("");
        itemNameField.setText("");
        itemPriceField.setText("");
        itemStackableBox.setSelected(false);
        itemMembersBox.setSelected(false);
        itemModelIdField.setText("");
        itemModelZoomField.setText("");
        itemRotationXField.setText("");
        itemRotationYField.setText("");
        itemOffsetXField.setText("");
        itemOffsetYField.setText("");
        itemEquipSlotField.setText("");
        itemEquipTypeField.setText("");
        itemMaleEquipField.setText("");
        itemFemaleEquipField.setText("");
        itemMaleWearOffsetXField.setText("");
        itemMaleWearOffsetYField.setText("");
        itemMaleWearOffsetZField.setText("");
        itemFemaleWearOffsetXField.setText("");
        itemFemaleWearOffsetYField.setText("");
        itemFemaleWearOffsetZField.setText("");
        itemCertField.setText("");
        itemLendField.setText("");
        itemTeamField.setText("");
        itemGroundOptionsArea.setText("");
        itemInventoryOptionsArea.setText("");
        itemTextureArea.setText("");
        itemDetailsArea.setText("");
        itemParamListModel.clear();
        itemRecolorListModel.clear();
        currentOriginalModelColors = new int[0];
        currentModifiedModelColors = new int[0];
        selectedHighlightedOriginalColors = new int[0];
        refreshRecolorEditor();
        currentItemParams = new LinkedHashMap<>();
        suppressPreviewUpdates = true;
        itemPreviewZoomSlider.setValue(2000);
        itemPreviewRotationXSlider.setValue(0);
        itemPreviewRotationYSlider.setValue(0);
        itemPreviewRotationZSlider.setValue(0);
        itemPreviewOffsetXSlider.setValue(0);
        itemPreviewOffsetYSlider.setValue(0);
        suppressPreviewUpdates = false;
        pushPreviewOverrides();
    }

    private void pushPreviewOverrides() {
        ItemDefinitionRecord current = itemPreviewPanel.item;
        boolean inventoryMode = itemPreviewModeCombo.getSelectedItem() == ItemPreviewMode.INVENTORY;
        int zoomOverride = inventoryMode ? itemPreviewZoomSlider.getValue() : itemPreviewZoomSlider.getValue();
        int rotationXOverride = inventoryMode
                ? current == null ? itemPreviewRotationXSlider.getValue() : itemPreviewRotationXSlider.getValue() - toSignedClientAngle(current.modelRotation1())
                : itemPreviewRotationXSlider.getValue();
        int rotationYOverride = inventoryMode
                ? current == null ? itemPreviewRotationYSlider.getValue() : itemPreviewRotationYSlider.getValue() - toSignedClientAngle(current.modelRotation2())
                : itemPreviewRotationYSlider.getValue();
        int rotationZOverride = inventoryMode
                ? current == null ? itemPreviewRotationZSlider.getValue() : itemPreviewRotationZSlider.getValue() - toSignedClientAngle(current.modelRotation3())
                : itemPreviewRotationZSlider.getValue();
        int offsetXOverride = inventoryMode
                ? current == null ? itemPreviewOffsetXSlider.getValue() : itemPreviewOffsetXSlider.getValue() - current.modelOffset1()
                : itemPreviewOffsetXSlider.getValue();
        int offsetYOverride = inventoryMode
                ? current == null ? itemPreviewOffsetYSlider.getValue() : itemPreviewOffsetYSlider.getValue() - current.modelOffset2()
                : itemPreviewOffsetYSlider.getValue();
        if (current != null) {
            appendLog("[item " + current.id() + "] preview values zoom=" + itemPreviewZoomSlider.getValue()
                    + " rot=" + itemPreviewRotationXSlider.getValue() + "/" + itemPreviewRotationYSlider.getValue() + "/" + itemPreviewRotationZSlider.getValue()
                    + " delta=" + rotationXOverride + "/" + rotationYOverride + "/" + rotationZOverride
                    + " off=" + itemPreviewOffsetXSlider.getValue() + "/" + itemPreviewOffsetYSlider.getValue());
        }
        updatePreviewValueLabels();
        itemPreviewPanel.setOverrides(
                zoomOverride,
                rotationXOverride,
                rotationYOverride,
                rotationZOverride,
                offsetXOverride,
                offsetYOverride
        );
    }

    private void applyPreviewModeDefaults(ItemPreviewMode previousMode, ItemPreviewMode nextMode) {
        if (nextMode == null || previousMode == nextMode) {
            return;
        }
        suppressPreviewUpdates = true;
        try {
            ItemDefinitionRecord current = itemPreviewPanel.item;
            if (current != null) {
                applyPreviewDefaultsForMode(nextMode, current);
            } else if (nextMode != ItemPreviewMode.INVENTORY && previousMode == ItemPreviewMode.INVENTORY) {
                itemPreviewZoomSlider.setValue(2300);
                itemPreviewRotationXSlider.setValue(0);
                itemPreviewRotationYSlider.setValue(0);
                itemPreviewRotationZSlider.setValue(0);
                itemPreviewOffsetXSlider.setValue(0);
                itemPreviewOffsetYSlider.setValue(80);
            }
        } finally {
            suppressPreviewUpdates = false;
            updatePreviewValueLabels();
            pushPreviewOverrides();
        }
    }

    private void applyPreviewDefaultsForMode(ItemPreviewMode mode, ItemDefinitionRecord item) {
        if (mode == null || item == null) {
            return;
        }
        if (mode == ItemPreviewMode.INVENTORY) {
            itemPreviewZoomSlider.setValue(item.modelZoom());
            itemPreviewRotationXSlider.setValue(toSignedClientAngle(item.modelRotation1()));
            itemPreviewRotationYSlider.setValue(toSignedClientAngle(item.modelRotation2()));
            itemPreviewRotationZSlider.setValue(toSignedClientAngle(item.modelRotation3()));
            itemPreviewOffsetXSlider.setValue(item.modelOffset1());
            itemPreviewOffsetYSlider.setValue(item.modelOffset2());
            return;
        }
        itemPreviewZoomSlider.setValue(frontFacingWornZoom(item));
        itemPreviewRotationXSlider.setValue(frontFacingWornPitch(item));
        itemPreviewRotationYSlider.setValue(frontFacingWornYaw(item));
        itemPreviewRotationZSlider.setValue(0);
        itemPreviewOffsetXSlider.setValue(0);
        itemPreviewOffsetYSlider.setValue(0);
    }

    private int frontFacingWornPitch(ItemDefinitionRecord item) {
        return -((item.equipSlot() == 3 || item.equipSlot() == 5) ? 120 : 104);
    }

    private int frontFacingWornYaw(ItemDefinitionRecord item) {
        return -((item.equipSlot() == 3 || item.equipSlot() == 5) ? 84 : 64);
    }

    private int frontFacingWornZoom(ItemDefinitionRecord item) {
        return switch (item.equipSlot()) {
            case 0, 1, 2, 4 -> 3100;
            case 3, 5 -> 3500;
            case 7 -> 2950;
            default -> 2850;
        };
    }

    private int toSignedClientAngle(int value) {
        int normalized = value & 2047;
        return normalized > 1023 ? normalized - 2048 : normalized;
    }

    private int currentWearOffsetXForPreview() {
        ItemDefinitionRecord current = itemPreviewPanel.item;
        ItemPreviewMode mode = (ItemPreviewMode) itemPreviewModeCombo.getSelectedItem();
        if (current == null || mode == null || mode == ItemPreviewMode.INVENTORY) {
            return 0;
        }
        return parseIntOrDefault(mode == ItemPreviewMode.FEMALE ? itemFemaleWearOffsetXField.getText() : itemMaleWearOffsetXField.getText(),
                mode == ItemPreviewMode.FEMALE ? current.femaleWearOffsetX() : current.maleWearOffsetX());
    }

    private int currentWearOffsetYForPreview() {
        ItemDefinitionRecord current = itemPreviewPanel.item;
        ItemPreviewMode mode = (ItemPreviewMode) itemPreviewModeCombo.getSelectedItem();
        if (current == null || mode == null || mode == ItemPreviewMode.INVENTORY) {
            return 0;
        }
        return parseIntOrDefault(mode == ItemPreviewMode.FEMALE ? itemFemaleWearOffsetYField.getText() : itemMaleWearOffsetYField.getText(),
                mode == ItemPreviewMode.FEMALE ? current.femaleWearOffsetY() : current.maleWearOffsetY());
    }

    private int currentWearOffsetZForPreview() {
        ItemDefinitionRecord current = itemPreviewPanel.item;
        ItemPreviewMode mode = (ItemPreviewMode) itemPreviewModeCombo.getSelectedItem();
        if (current == null || mode == null || mode == ItemPreviewMode.INVENTORY) {
            return 0;
        }
        return parseIntOrDefault(mode == ItemPreviewMode.FEMALE ? itemFemaleWearOffsetZField.getText() : itemMaleWearOffsetZField.getText(),
                mode == ItemPreviewMode.FEMALE ? current.femaleWearOffsetZ() : current.maleWearOffsetZ());
    }

    private int parseIntOrDefault(String text, int fallback) {
        if (text == null) {
            return fallback;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(trimmed);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private void updatePreviewValueLabels() {
        itemPreviewZoomValueLabel.setText(String.valueOf(itemPreviewZoomSlider.getValue()));
        itemPreviewRotationXValueLabel.setText(String.valueOf(itemPreviewRotationXSlider.getValue()));
        itemPreviewRotationYValueLabel.setText(String.valueOf(itemPreviewRotationYSlider.getValue()));
        itemPreviewRotationZValueLabel.setText(String.valueOf(itemPreviewRotationZSlider.getValue()));
        itemPreviewOffsetXValueLabel.setText(String.valueOf(itemPreviewOffsetXSlider.getValue()));
        itemPreviewOffsetYValueLabel.setText(String.valueOf(itemPreviewOffsetYSlider.getValue()));
    }

    private String joinInts(int first, int second) {
        return first + " / " + second;
    }

    private String joinLines(String[] values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            builder.append(i).append(": ").append(values[i] == null ? "-" : values[i]).append('\n');
        }
        return builder.toString().trim();
    }

    private String joinPairs(int[] original, int[] modified) {
        if (original == null || modified == null || original.length == 0 || modified.length == 0) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        int count = Math.min(original.length, modified.length);
        for (int i = 0; i < count; i++) {
            builder.append(original[i]).append(" -> ").append(modified[i]).append('\n');
        }
        return builder.toString().trim();
    }

    private String joinPairs(short[] original, short[] modified) {
        if (original == null || modified == null || original.length == 0 || modified.length == 0) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        int count = Math.min(original.length, modified.length);
        for (int i = 0; i < count; i++) {
            builder.append(original[i]).append(" -> ").append(modified[i]).append('\n');
        }
        return builder.toString().trim();
    }

    private String formatClientScriptData(Map<Integer, Object> params) {
        if (params == null || params.isEmpty()) {
            return "no client params";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<Integer, Object> entry : params.entrySet()) {
            builder.append(entry.getKey()).append(" = ").append(entry.getValue()).append('\n');
        }
        return builder.toString().trim();
    }

    private void refreshItemParamsView() {
        itemParamListModel.clear();
        currentItemParams.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> itemParamListModel.addElement(formatParamListEntry(entry.getKey(), entry.getValue())));
        itemDetailsArea.setText(formatClientScriptData(currentItemParams));
        itemDetailsArea.setCaretPosition(0);
    }

    private void refreshRecolorList(ItemDefinitionRecord item, int preferredOriginalColor) {
        itemRecolorListModel.clear();
        if (itemModelRenderer == null || item == null || item.modelId() < 0) {
            refreshRecolorEditor();
            return;
        }
        Map<Integer, Integer> existingRecolors = new LinkedHashMap<>();
        int[] itemOriginalColors = item.originalModelColors() == null ? new int[0] : item.originalModelColors();
        int[] itemModifiedColors = item.modifiedModelColors() == null ? new int[0] : item.modifiedModelColors();
        for (int i = 0; i < Math.min(itemOriginalColors.length, itemModifiedColors.length); i++) {
            existingRecolors.put(itemOriginalColors[i] & 0xFFFF, itemModifiedColors[i] & 0xFFFF);
        }
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        ItemPreviewMode mode = (ItemPreviewMode) itemPreviewModeCombo.getSelectedItem();
        List<ItemModelRenderer.ModelColorInfo> modelColors = switch (mode == null ? ItemPreviewMode.INVENTORY : mode) {
            case INVENTORY -> itemModelRenderer.listModelColors(item.modelId());
            case MALE -> itemModelRenderer.listWornModelColors(item, false, currentOriginalModelColors, currentModifiedModelColors);
            case FEMALE -> itemModelRenderer.listWornModelColors(item, true, currentOriginalModelColors, currentModifiedModelColors);
        };
        currentOriginalModelColors = new int[modelColors.size()];
        currentModifiedModelColors = new int[modelColors.size()];
        for (ItemModelRenderer.ModelColorInfo info : modelColors) {
            counts.put(info.color(), info.count());
        }
        for (int i = 0; i < modelColors.size(); i++) {
            int original = modelColors.get(i).color() & 0xFFFF;
            int modified = existingRecolors.getOrDefault(original, original);
            currentOriginalModelColors[i] = original;
            currentModifiedModelColors[i] = modified;
            itemRecolorListModel.addElement(new RecolorEntry(i, original, modified, counts.getOrDefault(original, 0)));
        }
        int selectionIndex = -1;
        if (preferredOriginalColor >= 0) {
            for (int i = 0; i < itemRecolorListModel.size(); i++) {
                if (itemRecolorListModel.get(i).originalColor() == preferredOriginalColor) {
                    selectionIndex = i;
                    break;
                }
            }
        }
        if (selectionIndex >= 0) {
            itemRecolorList.setSelectedIndex(selectionIndex);
        } else {
            itemRecolorList.clearSelection();
        }
        refreshRecolorEditor();
    }

    private void refreshRecolorEditor() {
        List<RecolorEntry> selectedEntries = itemRecolorList.getSelectedValuesList();
        RecolorEntry entry = selectedEntries.isEmpty() ? null : selectedEntries.get(0);
        suppressRecolorUpdates = true;
        if (entry == null) {
            recolorHueSlider.setValue(0);
            recolorSaturationSlider.setValue(0);
            recolorLightnessSlider.setValue(0);
            recolorPackedValueField.setText("");
            recolorOriginalSwatch.setBackground(PANEL_ALT);
            recolorModifiedSwatch.setBackground(PANEL_ALT);
        } else {
            int color = entry.modifiedColor & 0xFFFF;
            recolorHueSlider.setValue((color >> 10) & 0x3F);
            recolorSaturationSlider.setValue((color >> 7) & 0x07);
            recolorLightnessSlider.setValue(color & 0x7F);
            recolorPackedValueField.setText(String.valueOf(color));
            recolorOriginalSwatch.setBackground(jagexColorToAwt(entry.originalColor));
            recolorModifiedSwatch.setBackground(jagexColorToAwt(color));
        }
        suppressRecolorUpdates = false;
    }

    private void applySelectedRecolor() {
        if (suppressRecolorUpdates) {
            return;
        }
        List<RecolorEntry> selectedEntries = itemRecolorList.getSelectedValuesList();
        if (selectedEntries.isEmpty()) {
            return;
        }
        int packed = packJagexColor(recolorHueSlider.getValue(), recolorSaturationSlider.getValue(), recolorLightnessSlider.getValue());
        for (RecolorEntry entry : selectedEntries) {
            if (entry.index < 0 || entry.index >= currentModifiedModelColors.length) {
                continue;
            }
            currentModifiedModelColors[entry.index] = packed;
            itemRecolorListModel.set(entry.index, new RecolorEntry(entry.index, entry.originalColor, packed, entry.faceCount));
        }
        recolorPackedValueField.setText(String.valueOf(packed));
        recolorModifiedSwatch.setBackground(jagexColorToAwt(packed));
        selectedHighlightedOriginalColors = new int[0];
        itemPreviewPanel.invalidateRenderKey();
        itemPreviewPanel.queueRenderNow();
    }

    private void applyPackedRecolorField() {
        if (suppressRecolorUpdates) {
            return;
        }
        String text = recolorPackedValueField.getText().trim();
        if (!isInteger(text)) {
            refreshRecolorEditor();
            return;
        }
        int packed = Math.max(0, Math.min(65535, Integer.parseInt(text)));
        suppressRecolorUpdates = true;
        recolorHueSlider.setValue((packed >> 10) & 0x3F);
        recolorSaturationSlider.setValue((packed >> 7) & 0x07);
        recolorLightnessSlider.setValue(packed & 0x7F);
        suppressRecolorUpdates = false;
        applySelectedRecolor();
    }

    private void openRecolorPicker() {
        RecolorEntry entry = itemRecolorList.getSelectedValue();
        if (entry == null) {
            return;
        }
        Color chosen = JColorChooser.showDialog(frame, "Select Colour", jagexColorToAwt(entry.modifiedColor));
        if (chosen == null) {
            return;
        }
        int packed = rgbToNearestJagexColor(chosen);
        suppressRecolorUpdates = true;
        recolorHueSlider.setValue((packed >> 10) & 0x3F);
        recolorSaturationSlider.setValue((packed >> 7) & 0x07);
        recolorLightnessSlider.setValue(packed & 0x7F);
        suppressRecolorUpdates = false;
        applySelectedRecolor();
        refreshRecolorEditor();
        itemPreviewPanel.invalidateRenderKey();
        itemPreviewPanel.queueRenderNow();
        itemPreviewPanel.repaint();
    }

    private int[] highlightedOriginalColorsFromSelection() {
        if (!recolorShowFacesBox.isSelected()) {
            return new int[0];
        }
        List<RecolorEntry> selectedEntries = itemRecolorList.getSelectedValuesList();
        if (selectedEntries.isEmpty()) {
            return new int[0];
        }
        return selectedEntries.stream().mapToInt(RecolorEntry::originalColor).distinct().toArray();
    }

    private static int packJagexColor(int hue, int saturation, int lightness) {
        return ((hue & 0x3F) << 10) | ((saturation & 0x07) << 7) | (lightness & 0x7F);
    }

    private static int rgbToNearestJagexColor(Color color) {
        int bestPacked = 0;
        long bestDistance = Long.MAX_VALUE;
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        for (int h = 0; h < 64; h++) {
            for (int s = 0; s < 8; s++) {
                for (int l = 0; l < 128; l++) {
                    int packed = packJagexColor(h, s, l);
                    Color candidate = jagexColorToAwt(packed);
                    long dr = r - candidate.getRed();
                    long dg = g - candidate.getGreen();
                    long db = b - candidate.getBlue();
                    long distance = dr * dr + dg * dg + db * db;
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestPacked = packed;
                    }
                }
            }
        }
        return bestPacked;
    }

    private static Color jagexColorToAwt(int packed) {
        double hue = ((packed >> 10) & 0x3F) / 64.0;
        double sat = ((packed >> 7) & 0x07) / 8.0;
        double light = (packed & 0x7F) / 128.0;
        sat = Math.min(1.0, sat * 1.25 + 0.08);
        light = Math.max(0.0, Math.min(1.0, light * 1.15));
        return new Color(hslToRgb(hue, sat, light));
    }

    private static int hslToRgb(double h, double s, double l) {
        double r;
        double g;
        double b;
        if (s == 0) {
            r = g = b = l;
        } else {
            double q = l < 0.5 ? l * (1 + s) : l + s - l * s;
            double p = 2 * l - q;
            r = hueToRgb(p, q, h + 1.0 / 3.0);
            g = hueToRgb(p, q, h);
            b = hueToRgb(p, q, h - 1.0 / 3.0);
        }
        return ((int) Math.round(r * 255) << 16) | ((int) Math.round(g * 255) << 8) | (int) Math.round(b * 255);
    }

    private static double hueToRgb(double p, double q, double t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1.0 / 6.0) return p + (q - p) * 6 * t;
        if (t < 1.0 / 2.0) return q;
        if (t < 2.0 / 3.0) return p + (q - p) * (2.0 / 3.0 - t) * 6;
        return p;
    }

    private String formatParamListEntry(int key, Object value) {
        ParamDefinition definition = ParamDefinition.byId(key);
        String label = definition == null ? "UNKNOWN" : definition.displayLabel();
        String type = value instanceof String ? "string" : "int";
        return key + " | " + label + " | " + type + " | " + value;
    }

    private void addItemParam() {
        if (currentItemId == null) {
            return;
        }
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(PANEL);
        JComboBox<ParamDefinition> knownParams = new JComboBox<>(ParamDefinition.values());
        knownParams.setSelectedIndex(0);
        styleCombo(knownParams);

        JTextField idField = createReadOnlyField();
        idField.setEditable(true);
        JTextField valueField = createReadOnlyField();
        valueField.setEditable(true);
        JComboBox<String> typeCombo = new JComboBox<>(new String[]{"int", "string"});
        styleCombo(typeCombo);
        JComboBox<WeaponTypeOption> weaponTypeCombo = new JComboBox<>(WeaponTypeOption.values());
        styleCombo(weaponTypeCombo);
        weaponTypeCombo.setVisible(false);

        ParamDefinition initial = (ParamDefinition) knownParams.getSelectedItem();
        if (initial != null) {
            idField.setText(String.valueOf(initial.id));
        }

        Runnable updateKnownValueInput = () -> {
            ParamDefinition selected = (ParamDefinition) knownParams.getSelectedItem();
            boolean weaponType = selected == ParamDefinition.WEAPON_TYPE && "int".equals(typeCombo.getSelectedItem());
            weaponTypeCombo.setVisible(weaponType);
            valueField.setVisible(!weaponType);
            if (weaponType && weaponTypeCombo.getSelectedItem() != null) {
                valueField.setText(String.valueOf(((WeaponTypeOption) weaponTypeCombo.getSelectedItem()).id));
            }
            panel.revalidate();
            panel.repaint();
        };

        knownParams.addActionListener(e -> {
            ParamDefinition selected = (ParamDefinition) knownParams.getSelectedItem();
            if (selected != null) {
                idField.setText(String.valueOf(selected.id));
            }
            updateKnownValueInput.run();
        });
        typeCombo.addActionListener(e -> updateKnownValueInput.run());
        weaponTypeCombo.addActionListener(e -> {
            WeaponTypeOption option = (WeaponTypeOption) weaponTypeCombo.getSelectedItem();
            if (option != null) {
                valueField.setText(String.valueOf(option.id));
            }
        });

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        panel.add(createLabel("Known", true), c);
        c.gridx = 1;
        panel.add(knownParams, c);
        c.gridx = 0;
        c.gridy = 1;
        panel.add(createLabel("Id", true), c);
        c.gridx = 1;
        panel.add(idField, c);
        c.gridx = 0;
        c.gridy = 2;
        panel.add(createLabel("Type", true), c);
        c.gridx = 1;
        panel.add(typeCombo, c);
        c.gridx = 0;
        c.gridy = 3;
        panel.add(createLabel("Value", true), c);
        c.gridx = 1;
        panel.add(valueField, c);
        panel.add(weaponTypeCombo, c);

        updateKnownValueInput.run();

        int result = JOptionPane.showConfirmDialog(frame, panel, "Add Item Param", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            int id = Integer.parseInt(idField.getText().trim());
            Object value = "string".equals(typeCombo.getSelectedItem()) ? valueField.getText() : Integer.parseInt(valueField.getText().trim());
            currentItemParams.put(id, value);
            refreshItemParamsView();
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(frame, "Param id/value must be valid numbers for int params.", "Invalid Param", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeSelectedItemParam() {
        String selected = itemParamList.getSelectedValue();
        if (selected == null) {
            return;
        }
        int separator = selected.indexOf(" | ");
        if (separator <= 0) {
            return;
        }
        int key = Integer.parseInt(selected.substring(0, separator));
        currentItemParams.remove(key);
        refreshItemParamsView();
    }

    private void applyItemParamText() {
        Map<Integer, Object> parsed = new LinkedHashMap<>();
        String[] lines = itemDetailsArea.getText().split("\\R");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.equalsIgnoreCase("no client params")) {
                continue;
            }
            int split = line.indexOf('=');
            if (split <= 0) {
                throw new IllegalArgumentException("Invalid param line: " + line);
            }
            int key = Integer.parseInt(line.substring(0, split).trim());
            String valueText = line.substring(split + 1).trim();
            Object value = isInteger(valueText) ? Integer.parseInt(valueText) : stripQuotes(valueText);
            parsed.put(key, value);
        }
        currentItemParams.clear();
        currentItemParams.putAll(new TreeMap<>(parsed));
        refreshItemParamsView();
    }

    private boolean isInteger(String value) {
        if (value.isEmpty()) {
            return false;
        }
        int start = value.charAt(0) == '-' ? 1 : 0;
        if (start == value.length()) {
            return false;
        }
        for (int i = start; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private String stripQuotes(String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private enum ParamDefinition {
        STAB_ATTACK(0, "stab attack"),
        SLASH_ATTACK(1, "slash attack"),
        CRUSH_ATTACK(2, "crush attack"),
        MAGIC_ATTACK(3, "magic attack"),
        RANGE_ATTACK(4, "range attack"),
        STAB_DEFENCE(5, "stab defence"),
        SLASH_DEFENCE(6, "slash defence"),
        CRUSH_DEFENCE(7, "crush defence"),
        MAGIC_DEFENCE(8, "magic defence"),
        RANGE_DEFENCE(9, "range defence"),
        PRAYER_BONUS(11, "prayer bonus"),
        ATTACK_SPEED(14, "attack speed"),
        UNKNOWN_23(23, "unknown"),
        SUMMONING_DEFENCE(417, "summoning defence"),
        EXTRA_OPTION(528, "extra option"),
        STRENGTH_BONUS(641, "strength bonus"),
        RANGE_STRENGTH(643, "range strength"),
        RENDER_ANIM(644, "render anim"),
        MAGIC_STRENGTH(685, "magic strength"),
        WEAPON_TYPE(686, "weapon type"),
        SPECIAL(687, "special"),
        SKILL_REQ_1(749, "skill req 1"),
        LEVEL_REQ_1(750, "level req 1"),
        SKILL_REQ_2(751, "skill req 2"),
        LEVEL_REQ_2(752, "level req 2"),
        ABSORB_MELEE(967, "absorb melee"),
        ABSORB_RANGE(968, "absorb range"),
        ABSORB_MAGIC(969, "absorb magic"),
        INFINITE_AIR(972, "infinite air"),
        INFINITE_WATER(973, "infinite water"),
        INFINITE_EARTH(974, "infinite earth"),
        INFINITE_FIRE(975, "infinite fire"),
        UNKNOWN_1397(1397, "rune/staff flag"),
        UNKNOWN_2195(2195, "unknown");

        private final int id;
        private final String label;

        ParamDefinition(int id, String label) {
            this.id = id;
            this.label = label;
        }

        private static ParamDefinition byId(int id) {
            for (ParamDefinition definition : values()) {
                if (definition.id == id) {
                    return definition;
                }
            }
            return null;
        }

        private String displayLabel() {
            return label.toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
        }

        @Override
        public String toString() {
            return id + " - " + displayLabel();
        }
    }

    private enum WeaponTypeOption {
        STAFF(1, "STAFF"),
        BATTLEAXE_DHAXE_HATCHET(2, "BATTLEAXE_DHAXE_HATCHET"),
        MJOLNIR_SCEPTRE(3, "MJOLNIR_SCEPTRE"),
        UNKNOWN_4(4, "UNKNOWN_4"),
        DAGGER_RAPIER_SWORD(5, "DAGGER_RAPIER_SWORD"),
        LONGSWORD(6, "LONGSWORD"),
        TWO_HANDED_SWORD_GODSWORD(7, "TWO_HANDED_SWORD_GODSWORD"),
        ANCHOR_MACE(8, "ANCHOR_MACE"),
        CLAWS(9, "CLAWS"),
        MAUL(10, "MAUL"),
        WHIP(11, "WHIP"),
        SPEAR(14, "SPEAR"),
        HALBERD(15, "HALBERD"),
        BOW(16, "BOW"),
        CROSSBOW(17, "CROSSBOW"),
        THROWN(18, "THROWN"),
        CHINCHOMPA(19, "CHINCHOMPA"),
        UNKNOWN_20(20, "UNKNOWN_20"),
        SALAMANDER(21, "SALAMANDER"),
        UNKNOWN_22(22, "UNKNOWN_22"),
        UNKNOWN_23(23, "UNKNOWN_23"),
        UNKNOWN_24(24, "UNKNOWN_24"),
        UNKNOWN_25(25, "UNKNOWN_25"),
        STAFF_OF_LIGHT(26, "STAFF_OF_LIGHT"),
        UNKNOWN_27(27, "UNKNOWN_27"),
        POLYPORE(28, "POLYPORE");

        private final int id;
        private final String label;

        WeaponTypeOption(int id, String label) {
            this.id = id;
            this.label = label;
        }

        @Override
        public String toString() {
            return id + " - " + label;
        }
    }

    private JTextArea createLogArea() {
        JTextArea area = new JTextArea(8, 20);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setEditable(false);
        area.setBackground(INPUT_BG);
        area.setForeground(MUTED);
        area.setCaretColor(TEXT);
        area.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        return area;
    }

    private ImageIcon loadIcon(Path path, int width, int height) {
        try {
            if (!Files.exists(path)) {
                return emptyIcon(width, height);
            }
            BufferedImage image = ImageIO.read(path.toFile());
            if (image == null) {
                return emptyIcon(width, height);
            }
            return new ImageIcon(image.getScaledInstance(width, height, Image.SCALE_SMOOTH));
        } catch (IOException e) {
            return emptyIcon(width, height);
        }
    }

    private ImageIcon emptyIcon(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(MUTED);
        graphics.drawRect(1, 1, width - 3, height - 3);
        graphics.dispose();
        return new ImageIcon(image);
    }

    private static String defaultCachePath() {
        Path path = PROJECT_ROOT.resolve("data").resolve("cache").normalize();
        return Files.exists(path) ? path.toString() : "data/cache/";
    }

    private static Path resolveUserPath(String raw) {
        Path path = Path.of(raw);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        Path direct = APP_BASE.resolve(path).normalize();
        if (Files.exists(direct)) {
            return direct;
        }
        return PROJECT_ROOT.resolve(path).normalize();
    }

    private record LoadResult(ItemDefinitionsService itemService, Exception error) {
        private static LoadResult success(ItemDefinitionsService itemService) {
            return new LoadResult(itemService, null);
        }

        private static LoadResult failure(Exception error) {
            return new LoadResult(null, error);
        }
    }

    private record ItemSelectionResult(int requestId, ItemDefinitionRecord item, String info) {
    }

    private record FilterResult(int requestId, List<Object> results, Object preferred, Object selected, boolean autoSelectIfEmpty) {
    }

    private record RecolorEntry(int index, int originalColor, int modifiedColor, int faceCount) {
        @Override
        public String toString() {
            return originalColor + " -> " + modifiedColor + " (" + faceCount + " faces)";
        }
    }

    private static final class FastListModel extends AbstractListModel<Object> {
        private List<Object> items = List.of();

        @Override
        public int getSize() {
            return items.size();
        }

        @Override
        public Object getElementAt(int index) {
            return items.get(index);
        }

        private void setItems(List<Object> nextItems) {
            items = List.copyOf(nextItems);
            fireContentsChanged(this, 0, Math.max(0, items.size() - 1));
        }

        private boolean contains(Object value) {
            return items.contains(value);
        }

        private boolean isEmpty() {
            return items.isEmpty();
        }
    }

    private final class ItemPreviewPanel extends JPanel {
        private static final int OVERLAY_ROTATE_SENSITIVITY = 3;
        private static final int OVERLAY_BUTTON_STEP = 24;
        private ItemDefinitionRecord item;
        private ItemModelRenderer renderer;
        private ItemPreviewMode mode = ItemPreviewMode.INVENTORY;
        private boolean overlayOnly;
        private int zoomOverride = 2000;
        private double overlayZoomFactor = 1.0;
        private int overlayPanX;
        private int overlayPanY;
        private Rectangle overlayImageBounds = new Rectangle();
        private Rectangle overlayImageContentBounds = new Rectangle();
        private Rectangle overlayViewportBounds = new Rectangle();
        private List<OverlayAxisControlLayout> overlayAxisControls = List.of();
        private JSlider activeOverlaySlider;
        private Point middleRotateAnchor;
        private int middleRotateBaseRotationX;
        private int middleRotateBaseRotationY;
        private int rotationXOverride;
        private int rotationYOverride;
        private int rotationZOverride;
        private int offsetXOverride;
        private int offsetYOverride;
        private BufferedImage renderedImage;
        private String renderedFailure;
        private boolean renderInProgress;
        private PreviewRenderKey renderKey;
        private int renderRequestId;
        private final Timer renderDebounceTimer;
        private final Timer loadingAnimationTimer;
        private SwingWorker<PreviewRenderResult, Void> activeRenderWorker;

        private ItemPreviewPanel() {
            setPreferredSize(new Dimension(470, 330));
            setMinimumSize(new Dimension(470, 330));
            setBackground(INPUT_BG);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    BorderFactory.createEmptyBorder(12, 12, 12, 12)
            ));
            renderDebounceTimer = new Timer(25, e -> queueRenderNow());
            renderDebounceTimer.setRepeats(false);
            loadingAnimationTimer = new Timer(100, e -> {
                if (renderInProgress) {
                    repaint();
                }
            });
            loadingAnimationTimer.setRepeats(true);
            addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent e) {
                    queueRender();
                }
            });
        }

        private void setRenderer(ItemModelRenderer renderer) {
            this.renderer = renderer;
            queueRenderNow();
            repaint();
        }

        private void setItem(ItemDefinitionRecord item) {
            this.item = item;
            queueRenderNow();
            repaint();
        }

        private void setItemSilently(ItemDefinitionRecord item) {
            appendLog("[item " + (item == null ? "null" : item.id()) + "] preview setItemSilently");
            cancelRenderWorker();
            renderRequestId++;
            this.item = item;
            this.renderedImage = null;
            this.renderedFailure = null;
            this.renderInProgress = false;
            this.renderKey = null;
            this.renderDebounceTimer.stop();
            this.loadingAnimationTimer.stop();
            this.overlayZoomFactor = 1.0;
            this.overlayPanX = 0;
            this.overlayPanY = 0;
            this.overlayImageBounds = new Rectangle();
            this.overlayImageContentBounds = new Rectangle();
            this.overlayViewportBounds = new Rectangle();
            this.overlayAxisControls = List.of();
            this.activeOverlaySlider = null;
            this.middleRotateAnchor = null;
            repaint();
        }

        private void setMode(ItemPreviewMode mode) {
            this.mode = mode == null ? ItemPreviewMode.INVENTORY : mode;
            this.renderKey = null;
            this.renderedImage = null;
            this.renderedFailure = null;
            this.overlayZoomFactor = 1.0;
            this.overlayPanX = 0;
            this.overlayPanY = 0;
            this.overlayImageBounds = new Rectangle();
            this.overlayImageContentBounds = new Rectangle();
            this.overlayViewportBounds = new Rectangle();
            this.overlayAxisControls = List.of();
            this.activeOverlaySlider = null;
            this.middleRotateAnchor = null;
            queueRenderNow();
            repaint();
        }

        private void setOverlayOnly(boolean overlayOnly) {
            this.overlayOnly = overlayOnly;
            repaint();
        }

        private void invalidateRenderKey() {
            this.renderKey = null;
        }

        private void adjustOverlayZoom(int wheelRotation, Point anchorPoint) {
            if (!overlayOnly) {
                return;
            }
            double oldZoom = overlayZoomFactor;
            double newZoom = Math.max(0.5, Math.min(4.0, overlayZoomFactor + (-wheelRotation * 0.1)));
            if (Math.abs(newZoom - oldZoom) < 0.0001) {
                return;
            }
            Rectangle anchorBounds = overlayImageContentBounds.width > 0 && overlayImageContentBounds.height > 0
                    ? overlayImageContentBounds
                    : overlayImageBounds;
            if (anchorPoint != null && anchorBounds.width > 0 && anchorBounds.height > 0 && renderedImage != null) {
                double anchorLocalX = anchorPoint.x - anchorBounds.x;
                double anchorLocalY = anchorPoint.y - anchorBounds.y;
                double relativeX = anchorLocalX / Math.max(1.0, anchorBounds.width);
                double relativeY = anchorLocalY / Math.max(1.0, anchorBounds.height);
                int oldWidth = anchorBounds.width;
                int oldHeight = anchorBounds.height;
                double zoomRatio = newZoom / Math.max(0.0001, oldZoom);
                int newWidth = Math.max(1, (int) Math.round(oldWidth * zoomRatio));
                int newHeight = Math.max(1, (int) Math.round(oldHeight * zoomRatio));
                overlayPanX += (int) Math.round((oldWidth - newWidth) * relativeX);
                overlayPanY += (int) Math.round((oldHeight - newHeight) * relativeY);
            }
            overlayZoomFactor = newZoom;
            queueRender();
            repaint();
        }

        private void adjustOverlayPan(int deltaX, int deltaY) {
            if (!overlayOnly) {
                return;
            }
            overlayPanX += deltaX;
            overlayPanY += deltaY;
            repaint();
        }

        private void setOverrides(int zoom, int rotationX, int rotationY, int rotationZ, int offsetX, int offsetY) {
            appendLog("[item " + (item == null ? "null" : item.id()) + "] preview setOverrides zoom=" + zoom
                    + " delta=" + rotationX + "/" + rotationY + "/" + rotationZ
                    + " off=" + offsetX + "/" + offsetY);
            this.zoomOverride = zoom;
            this.rotationXOverride = rotationX;
            this.rotationYOverride = rotationY;
            this.rotationZOverride = rotationZ;
            this.offsetXOverride = offsetX;
            this.offsetYOverride = offsetY;
            queueRender();
            repaint();
        }

        private void clear() {
            cancelRenderWorker();
            renderRequestId++;
            this.item = null;
            this.renderedImage = null;
            this.renderedFailure = null;
            this.renderInProgress = false;
            this.renderKey = null;
            this.renderDebounceTimer.stop();
            this.loadingAnimationTimer.stop();
            this.overlayZoomFactor = 1.0;
            this.overlayPanX = 0;
            this.overlayPanY = 0;
            this.overlayImageBounds = new Rectangle();
            this.overlayImageContentBounds = new Rectangle();
            this.overlayViewportBounds = new Rectangle();
            this.overlayAxisControls = List.of();
            this.activeOverlaySlider = null;
            this.middleRotateAnchor = null;
            repaint();
        }

        private boolean beginOverlayInteraction(java.awt.event.MouseEvent event) {
            if (!overlayOnly || mode == ItemPreviewMode.INVENTORY) {
                return false;
            }
            Point point = event.getPoint();
            if (SwingUtilities.isMiddleMouseButton(event) && overlayViewportBounds.contains(point)) {
                middleRotateAnchor = point;
                middleRotateBaseRotationX = itemPreviewRotationXSlider.getValue();
                middleRotateBaseRotationY = itemPreviewRotationYSlider.getValue();
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                return true;
            }
            if (!SwingUtilities.isLeftMouseButton(event)) {
                return false;
            }
            OverlayAxisControlLayout control = findOverlayAxisControl(point);
            if (control == null) {
                return false;
            }
            if (control.minusBounds().contains(point)) {
                adjustPreviewSlider(control.slider(), -OVERLAY_BUTTON_STEP);
            } else if (control.plusBounds().contains(point)) {
                adjustPreviewSlider(control.slider(), OVERLAY_BUTTON_STEP);
            } else if (control.trackBounds().contains(point)) {
                activeOverlaySlider = control.slider();
                setOverlaySliderFromPoint(control, point);
            }
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            return true;
        }

        private boolean handleOverlayDrag(java.awt.event.MouseEvent event) {
            if (!overlayOnly || mode == ItemPreviewMode.INVENTORY) {
                return false;
            }
            if (activeOverlaySlider != null && SwingUtilities.isLeftMouseButton(event)) {
                OverlayAxisControlLayout control = findOverlayAxisControl(activeOverlaySlider);
                if (control != null) {
                    setOverlaySliderFromPoint(control, event.getPoint());
                    return true;
                }
            }
            if (middleRotateAnchor != null && SwingUtilities.isMiddleMouseButton(event)) {
                int deltaX = event.getX() - middleRotateAnchor.x;
                int deltaY = event.getY() - middleRotateAnchor.y;
                itemPreviewRotationYSlider.setValue(clampPreviewSliderValue(itemPreviewRotationYSlider, middleRotateBaseRotationY - (deltaX * OVERLAY_ROTATE_SENSITIVITY)));
                itemPreviewRotationXSlider.setValue(clampPreviewSliderValue(itemPreviewRotationXSlider, middleRotateBaseRotationX + (deltaY * OVERLAY_ROTATE_SENSITIVITY)));
                return true;
            }
            return false;
        }

        private void endOverlayInteraction(Point point) {
            boolean hadInteraction = activeOverlaySlider != null || middleRotateAnchor != null;
            activeOverlaySlider = null;
            middleRotateAnchor = null;
            if (hadInteraction && overlayOnly && mode != ItemPreviewMode.INVENTORY) {
                queueRenderNow();
            }
            updateOverlayCursor(point);
        }

        private void updateOverlayCursor(Point point) {
            if (!overlayOnly || mode == ItemPreviewMode.INVENTORY) {
                setCursor(Cursor.getDefaultCursor());
                return;
            }
            if (point != null && (overlayViewportBounds.contains(point) || findOverlayAxisControl(point) != null)) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                return;
            }
            setCursor(Cursor.getDefaultCursor());
        }

        private OverlayAxisControlLayout findOverlayAxisControl(Point point) {
            if (point == null) {
                return null;
            }
            for (OverlayAxisControlLayout control : overlayAxisControls) {
                if (control.minusBounds().contains(point) || control.plusBounds().contains(point) || control.trackBounds().contains(point)) {
                    return control;
                }
            }
            return null;
        }

        private OverlayAxisControlLayout findOverlayAxisControl(JSlider slider) {
            for (OverlayAxisControlLayout control : overlayAxisControls) {
                if (control.slider() == slider) {
                    return control;
                }
            }
            return null;
        }

        private void setOverlaySliderFromPoint(OverlayAxisControlLayout control, Point point) {
            Rectangle track = control.trackBounds();
            if (track.width <= 1) {
                return;
            }
            double ratio = (point.x - track.x) / (double) track.width;
            ratio = Math.max(0.0, Math.min(1.0, ratio));
            JSlider slider = control.slider();
            int range = slider.getMaximum() - slider.getMinimum();
            slider.setValue(slider.getMinimum() + (int) Math.round(range * ratio));
        }

        private int clampPreviewSliderValue(JSlider slider, int value) {
            return Math.max(slider.getMinimum(), Math.min(slider.getMaximum(), value));
        }

        private void queueRender() {
            if (item == null || renderer == null) {
                queueRenderNow();
                return;
            }
            renderDebounceTimer.restart();
        }

        private void queueRenderNow() {
            if (item == null || renderer == null) {
                cancelRenderWorker();
                renderedImage = null;
                renderedFailure = null;
                renderInProgress = false;
                renderKey = null;
                return;
            }
            int targetWidth = mode == ItemPreviewMode.INVENTORY
                    ? 108
                    : Math.max(220, Math.min(320, Math.min(Math.max(0, getWidth() - 120), Math.max(0, getHeight() - 40))));
            int targetHeight = mode == ItemPreviewMode.INVENTORY ? 96 : Math.max(340, (int) Math.round(targetWidth * 1.6));
            double overlayRenderScale = overlayRenderScale();
            targetWidth = Math.max(1, (int) Math.round(targetWidth * overlayRenderScale));
            targetHeight = Math.max(1, (int) Math.round(targetHeight * overlayRenderScale));
            final int renderTargetWidth = targetWidth;
            final int renderTargetHeight = targetHeight;
            int animationFrame = mode == ItemPreviewMode.INVENTORY ? 0 : (int) ((System.currentTimeMillis() / 90L) % 48L);
            final ItemDefinitionRecord renderItem = item;
            final ItemPreviewMode renderMode = mode;
            final int renderZoomOverride = zoomOverride;
            final int renderRotationXOverride = rotationXOverride;
            final int renderRotationYOverride = rotationYOverride;
            final int renderRotationZOverride = rotationZOverride;
            final int renderOffsetXOverride = offsetXOverride;
            final int renderOffsetYOverride = offsetYOverride;
            final int renderWearOffsetX = currentWearOffsetXForPreview();
            final int renderWearOffsetY = currentWearOffsetYForPreview();
            final int renderWearOffsetZ = currentWearOffsetZForPreview();
            PreviewRenderKey nextKey = new PreviewRenderKey(renderItem.id(), renderMode, renderItem.modelId(), renderItem.maleEquip1(), renderItem.maleEquip2(), renderItem.maleEquip3(), renderItem.femaleEquip1(), renderItem.femaleEquip2(), renderItem.femaleEquip3(), renderZoomOverride, renderRotationXOverride, renderRotationYOverride, renderRotationZOverride, renderOffsetXOverride, renderOffsetYOverride, animationFrame, renderTargetWidth, renderTargetHeight);
            if (nextKey.equals(renderKey) && (renderInProgress || renderedImage != null || renderedFailure != null)) {
                return;
            }
            cancelRenderWorker();
            renderKey = nextKey;
            renderInProgress = true;
            boolean initialLoad = renderedImage == null;
            renderedFailure = null;
            if (initialLoad) {
                loadingAnimationTimer.start();
            } else {
                loadingAnimationTimer.stop();
            }
            repaint();
            appendLog("[item " + renderItem.id() + "] preview queue mode=" + renderMode + " model=" + renderItem.modelId() + " target=" + renderTargetWidth + "x" + renderTargetHeight
                    + " zoom=" + renderZoomOverride + " rot=" + renderRotationXOverride + "/" + renderRotationYOverride + "/" + renderRotationZOverride
                    + " off=" + renderOffsetXOverride + "/" + renderOffsetYOverride);
            int requestId = ++renderRequestId;
            final int workerItemId = renderItem.id();
            SwingWorker<PreviewRenderResult, Void> worker = new SwingWorker<>() {
                @Override
                protected PreviewRenderResult doInBackground() {
                    try {
                        if (isCancelled()) {
                            return new PreviewRenderResult(null, "preview render cancelled");
                        }
                        appendLog("[item " + renderItem.id() + "] preview worker start");
                        BufferedImage image = switch (renderMode) {
                            case INVENTORY -> renderer.renderInventory(renderItem, currentOriginalModelColors, currentModifiedModelColors, renderTargetWidth, renderTargetHeight, applyPreviewZoomBoost(renderZoomOverride), renderRotationXOverride, renderRotationYOverride, renderRotationZOverride, renderOffsetXOverride, renderOffsetYOverride, selectedHighlightedOriginalColors);
                            case MALE -> renderer.renderWorn(renderItem, currentOriginalModelColors, currentModifiedModelColors, false, renderTargetWidth, renderTargetHeight,
                                    renderZoomOverride, renderRotationXOverride, renderRotationYOverride, renderRotationZOverride, renderOffsetXOverride, renderOffsetYOverride, renderWearOffsetX, renderWearOffsetY, renderWearOffsetZ, animationFrame, selectedHighlightedOriginalColors);
                            case FEMALE -> renderer.renderWorn(renderItem, currentOriginalModelColors, currentModifiedModelColors, true, renderTargetWidth, renderTargetHeight,
                                    renderZoomOverride, renderRotationXOverride, renderRotationYOverride, renderRotationZOverride, renderOffsetXOverride, renderOffsetYOverride, renderWearOffsetX, renderWearOffsetY, renderWearOffsetZ, animationFrame, selectedHighlightedOriginalColors);
                        };
                        if (image == null) {
                            try {
                                Thread.sleep(60L);
                            } catch (InterruptedException interruptedException) {
                                Thread.currentThread().interrupt();
                            }
                            image = switch (renderMode) {
                                case INVENTORY -> renderer.renderInventory(renderItem, currentOriginalModelColors, currentModifiedModelColors, renderTargetWidth, renderTargetHeight, applyPreviewZoomBoost(renderZoomOverride), renderRotationXOverride, renderRotationYOverride, renderRotationZOverride, renderOffsetXOverride, renderOffsetYOverride, selectedHighlightedOriginalColors);
                                case MALE -> renderer.renderWorn(renderItem, currentOriginalModelColors, currentModifiedModelColors, false, renderTargetWidth, renderTargetHeight,
                                        renderZoomOverride, renderRotationXOverride, renderRotationYOverride, renderRotationZOverride, renderOffsetXOverride, renderOffsetYOverride, renderWearOffsetX, renderWearOffsetY, renderWearOffsetZ, animationFrame, selectedHighlightedOriginalColors);
                                case FEMALE -> renderer.renderWorn(renderItem, currentOriginalModelColors, currentModifiedModelColors, true, renderTargetWidth, renderTargetHeight,
                                        renderZoomOverride, renderRotationXOverride, renderRotationYOverride, renderRotationZOverride, renderOffsetXOverride, renderOffsetYOverride, renderWearOffsetX, renderWearOffsetY, renderWearOffsetZ, animationFrame, selectedHighlightedOriginalColors);
                            };
                        }
                        int activeModelId = switch (renderMode) {
                            case INVENTORY -> renderItem.modelId();
                            case MALE -> renderItem.maleEquip1();
                            case FEMALE -> renderItem.femaleEquip1();
                        };
                        try {
                            appendLog(renderer.debugDescribeModel(activeModelId));
                        } catch (RuntimeException ignored) {
                        }
                        String failure = image == null ? renderer.getFailureReason(activeModelId) : null;
                        appendLog("[item " + renderItem.id() + "] preview worker done image=" + (image != null) + (failure == null ? "" : " failure=" + failure));
                        return new PreviewRenderResult(image, failure);
                    } catch (RuntimeException exception) {
                        appendLog("[item " + renderItem.id() + "] preview worker fail " + exception.getClass().getSimpleName() + (exception.getMessage() == null ? "" : ": " + exception.getMessage()));
                        return new PreviewRenderResult(null, exception.getClass().getSimpleName() + (exception.getMessage() == null ? "" : ": " + exception.getMessage()));
                    }
                }

                @Override
                protected void done() {
                    appendLog("[item " + workerItemId + "] preview done enter");
                    if (requestId != renderRequestId) {
                        appendLog("[item " + workerItemId + "] preview done stale request");
                        return;
                    }
                    if (isCancelled()) {
                        appendLog("[item " + workerItemId + "] preview done cancelled");
                        renderInProgress = false;
                        loadingAnimationTimer.stop();
                        return;
                    }
                    try {
                        appendLog("[item " + workerItemId + "] preview done before get");
                        PreviewRenderResult result = get();
                        appendLog("[item " + workerItemId + "] preview done after get image=" + (result.image() != null));
                        if (result.image() != null) {
                            renderedImage = result.image();
                            renderedFailure = null;
                            appendLog("[item " + workerItemId + "] preview done assigned image");
                        } else if (renderedImage == null) {
                            renderedFailure = result.failure();
                            appendLog("[item " + workerItemId + "] preview done assigned failure");
                        }
                    } catch (Exception exception) {
                        appendLog("[item " + workerItemId + "] preview done catch " + exception.getClass().getSimpleName() + (exception.getMessage() == null ? "" : ": " + exception.getMessage()));
                        if (renderedImage == null) {
                            renderedFailure = exception.getClass().getSimpleName() + (exception.getMessage() == null ? "" : ": " + exception.getMessage());
                        }
                    } finally {
                        renderInProgress = false;
                        loadingAnimationTimer.stop();
                        appendLog("[item " + workerItemId + "] preview done repaint");
                        repaint();
                    }
                }
            };
            activeRenderWorker = worker;
            worker.execute();
        }

        private void cancelRenderWorker() {
            if (activeRenderWorker != null && !activeRenderWorker.isDone()) {
                appendLog("[item " + (item == null ? "null" : item.id()) + "] preview cancel active worker");
                activeRenderWorker.cancel(true);
            }
            activeRenderWorker = null;
            loadingAnimationTimer.stop();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            if (overlayOnly) {
                if (mode == ItemPreviewMode.INVENTORY) {
                    int slotWidth = 108;
                    int slotHeight = 110;
                    int slotX = Math.max(8, (w - slotWidth) / 2);
                    int slotY = Math.max(8, (h - slotHeight) / 2);
                    previewOverlayDragBounds = new Rectangle(slotX, slotY, slotWidth, slotHeight);
                    overlayViewportBounds = new Rectangle(slotX, slotY, slotWidth, slotHeight);
                    overlayAxisControls = List.of();
                    g2.setColor(new Color(56, 56, 56));
                    g2.fillRoundRect(slotX, slotY, slotWidth, slotHeight, 18, 18);
                    g2.setColor(new Color(90, 90, 90));
                    g2.drawRoundRect(slotX, slotY, slotWidth, slotHeight, 18, 18);

                    if (renderedImage != null) {
                        double drawScale = overlayDisplayScale();
                        int drawWidth = Math.max(1, (int) Math.round(renderedImage.getWidth() * drawScale));
                        int drawHeight = Math.max(1, (int) Math.round(renderedImage.getHeight() * drawScale));
                        Rectangle contentBounds = computeVisibleImageBounds(renderedImage);
                        int contentWidth = Math.max(1, (int) Math.round(contentBounds.width * drawScale));
                        int contentHeight = Math.max(1, (int) Math.round(contentBounds.height * drawScale));
                        int drawX = slotX + (slotWidth - contentWidth) / 2 - (int) Math.round(contentBounds.x * drawScale) + overlayPanX;
                        int drawY = slotY + (slotHeight - contentHeight) / 2 - (int) Math.round(contentBounds.y * drawScale) + overlayPanY;
                        overlayImageBounds = new Rectangle(drawX, drawY, drawWidth, drawHeight);
                        overlayImageContentBounds = new Rectangle(
                                drawX + (int) Math.round(contentBounds.x * drawScale),
                                drawY + (int) Math.round(contentBounds.y * drawScale),
                                Math.max(1, (int) Math.round(contentBounds.width * drawScale)),
                                Math.max(1, (int) Math.round(contentBounds.height * drawScale))
                        );
                        Shape previousClip = g2.getClip();
                        g2.setClip(new java.awt.geom.RoundRectangle2D.Double(slotX, slotY, slotWidth, slotHeight, 18, 18));
                        g2.drawImage(renderedImage, drawX, drawY, drawWidth, drawHeight, null);
                        g2.setClip(previousClip);
                    } else if (renderInProgress || renderedFailure == null) {
                        overlayImageBounds = new Rectangle();
                        overlayImageContentBounds = new Rectangle();
                        drawLoadingOrb(g2, slotX + slotWidth / 2, slotY + slotHeight / 2 - 6);
                    }
                    g2.dispose();
                    return;
                }

                int frameX = 8;
                int frameY = 8;
                int frameW = Math.max(240, w - 16);
                int frameH = Math.max(360, h - 16);
                int headerH = 34;
                int footerH = 140;
                int viewportX = frameX + 12;
                int viewportY = frameY + headerH;
                int viewportW = frameW - 24;
                int viewportH = Math.max(220, frameH - headerH - footerH - 16);
                int footerY = viewportY + viewportH + 8;
                previewOverlayDragBounds = new Rectangle(frameX, frameY, frameW, frameH);
                overlayViewportBounds = new Rectangle(viewportX, viewportY, viewportW, viewportH);

                g2.setColor(new Color(56, 56, 56));
                g2.fillRoundRect(viewportX, viewportY, viewportW, viewportH, 18, 18);
                g2.setColor(new Color(90, 90, 90));
                g2.drawRoundRect(viewportX, viewportY, viewportW, viewportH, 18, 18);

                g2.setFont(g2.getFont().deriveFont(Font.BOLD, 12f));
                g2.setColor(TEXT);
                g2.drawString(mode.label + " View", frameX + 14, frameY + 22);
                g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 11f));
                g2.setColor(MUTED);
                g2.drawString("MMB drag rotate  RMB pan  Wheel zoom", frameX + 108, frameY + 22);

                if (renderedImage != null) {
                    double drawScale = overlayDisplayScale();
                    int drawWidth = Math.max(1, (int) Math.round(renderedImage.getWidth() * drawScale));
                    int drawHeight = Math.max(1, (int) Math.round(renderedImage.getHeight() * drawScale));
                    Rectangle contentBounds = computeVisibleImageBounds(renderedImage);
                    int contentWidth = Math.max(1, (int) Math.round(contentBounds.width * drawScale));
                    int contentHeight = Math.max(1, (int) Math.round(contentBounds.height * drawScale));
                    int drawX = viewportX + (viewportW - contentWidth) / 2 - (int) Math.round(contentBounds.x * drawScale) + overlayPanX;
                    int drawY = viewportY + (viewportH - contentHeight) / 2 - (int) Math.round(contentBounds.y * drawScale) + overlayPanY;
                    overlayImageBounds = new Rectangle(drawX, drawY, drawWidth, drawHeight);
                    overlayImageContentBounds = new Rectangle(
                            drawX + (int) Math.round(contentBounds.x * drawScale),
                            drawY + (int) Math.round(contentBounds.y * drawScale),
                            Math.max(1, (int) Math.round(contentBounds.width * drawScale)),
                            Math.max(1, (int) Math.round(contentBounds.height * drawScale))
                    );
                    Shape previousClip = g2.getClip();
                    g2.setClip(new java.awt.geom.RoundRectangle2D.Double(viewportX, viewportY, viewportW, viewportH, 18, 18));
                    g2.drawImage(renderedImage, drawX, drawY, drawWidth, drawHeight, null);
                    g2.setClip(previousClip);
                } else if (renderInProgress || renderedFailure == null) {
                    overlayImageBounds = new Rectangle();
                    overlayImageContentBounds = new Rectangle();
                    drawLoadingOrb(g2, viewportX + viewportW / 2, viewportY + viewportH / 2 - 6);
                }

                g2.setColor(new Color(30, 30, 30, 218));
                g2.fillRoundRect(viewportX, footerY, viewportW, footerH, 16, 16);
                g2.setColor(new Color(92, 92, 92));
                g2.drawRoundRect(viewportX, footerY, viewportW, footerH, 16, 16);
                overlayAxisControls = buildOverlayAxisControls(viewportX + 12, footerY + 12, viewportW - 24, 22);
                for (OverlayAxisControlLayout control : overlayAxisControls) {
                    drawOverlayAxisControl(g2, control);
                }

                g2.dispose();
                return;
            }

            previewOverlayDragBounds = new Rectangle();
            overlayImageBounds = new Rectangle();
            overlayImageContentBounds = new Rectangle();
            overlayViewportBounds = new Rectangle();
            overlayAxisControls = List.of();

            g2.setColor(new Color(24, 24, 24));
            g2.fillRoundRect(16, 16, w - 32, h - 32, 20, 20);

            int headerY = 38;
            int slotWidth = mode == ItemPreviewMode.INVENTORY ? 108 : 220;
            int slotHeight = mode == ItemPreviewMode.INVENTORY ? 110 : 300;
            int slotX = 34;
            int slotY = Math.max(44, (h - slotHeight) / 2 - 24);

            g2.setColor(new Color(56, 56, 56));
            g2.fillRoundRect(slotX, slotY, slotWidth, slotHeight, 18, 18);
            g2.setColor(new Color(90, 90, 90));
            g2.drawRoundRect(slotX, slotY, slotWidth, slotHeight, 18, 18);

            if (item == null) {
                g2.setColor(MUTED);
                g2.drawString("No item loaded", slotX + 18, slotY + 28);
                g2.dispose();
                return;
            }

            g2.setColor(TEXT);
            Font originalFont = g2.getFont();
            g2.setFont(originalFont.deriveFont(Font.BOLD, 14f));
            drawClippedString(g2, item.name(), slotX, headerY, slotWidth, false);

            int activeModelId = switch (mode) {
                case INVENTORY -> item.modelId();
                case MALE -> item.maleEquip1();
                case FEMALE -> item.femaleEquip1();
            };
            int secondaryModelId = switch (mode) {
                case INVENTORY -> -1;
                case MALE -> item.maleEquip2();
                case FEMALE -> item.femaleEquip2();
            };
            if (renderedImage != null) {
                int drawX = slotX + (slotWidth - renderedImage.getWidth()) / 2;
                int drawY = slotY + Math.max(8, (slotHeight - renderedImage.getHeight()) / 2 - 10);
                g2.drawImage(renderedImage, drawX, drawY, null);
                if (renderInProgress) {
                    g2.setColor(new Color(0, 0, 0, 90));
                    g2.fillRoundRect(slotX + 8, slotY + 8, 96, 20, 10, 10);
                    g2.setColor(TEXT);
                    g2.setFont(originalFont.deriveFont(Font.PLAIN, 11f));
                    g2.drawString("Updating...", slotX + 18, slotY + 22);
                }
            } else {
                if (renderInProgress || renderedFailure == null) {
                    drawLoadingOrb(g2, slotX + slotWidth / 2, slotY + slotHeight / 2 - 6);
                    g2.setColor(TEXT);
                    g2.setFont(originalFont.deriveFont(Font.PLAIN, 12f));
                    String loadingText = "Loading preview";
                    int textWidth = g2.getFontMetrics().stringWidth(loadingText);
                    g2.drawString(loadingText, slotX + (slotWidth - textWidth) / 2, slotY + slotHeight / 2 + 24);
                } else {
                    g2.setColor(new Color(220, 138, 0, 140));
                    g2.drawString("Model " + activeModelId + " unavailable", slotX + 14, slotY + 28);
                }
                if (!renderInProgress && renderedFailure != null && !renderedFailure.isBlank()) {
                    g2.setColor(MUTED);
                    g2.setFont(originalFont.deriveFont(Font.PLAIN, 11f));
                    drawClippedString(g2, renderedFailure, slotX + 14, slotY + 46, slotWidth - 20, true);
                }
            }

            int infoX = slotX + slotWidth + 28;
            int infoW = Math.max(150, w - infoX - 30);
            int infoH = slotHeight;
            int infoY = slotY;
            g2.setColor(new Color(30, 30, 30, 210));
            g2.fillRoundRect(infoX - 12, infoY - 10, infoW + 20, infoH, 16, 16);
            g2.setColor(new Color(92, 92, 92));
            g2.drawRoundRect(infoX - 12, infoY - 10, infoW + 20, infoH, 16, 16);
            g2.setColor(TEXT);
            g2.setFont(originalFont.deriveFont(Font.BOLD, 12.5f));
            g2.drawString("Preview Info", infoX, infoY + 8);
            g2.setFont(originalFont.deriveFont(Font.PLAIN, 12f));
            drawCompactLine(g2, "Page", mode.label, infoX, infoY + 32, infoW);
            drawCompactLine(g2, "Model", String.valueOf(activeModelId), infoX, infoY + 54, infoW);
            drawCompactLine(g2, "Model 2", String.valueOf(secondaryModelId), infoX, infoY + 76, infoW);
            if (slotHeight >= 118) {
                drawCompactLine(g2, "Zoom", String.valueOf(zoomOverride), infoX, infoY + 98, infoW);
            }
            g2.dispose();
        }

        private Rectangle computeVisibleImageBounds(BufferedImage image) {
            int minX = image.getWidth();
            int minY = image.getHeight();
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if (((image.getRGB(x, y) >>> 24) & 0xFF) == 0) {
                        continue;
                    }
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
            if (maxX < minX || maxY < minY) {
                return new Rectangle(0, 0, image.getWidth(), image.getHeight());
            }
            return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
        }

        private void drawCompactLine(Graphics2D g2, String label, String value, int x, int y, int width) {
            g2.setColor(new Color(196, 196, 196));
            g2.drawString(label + ":", x, y);
            g2.setColor(TEXT);
            drawClippedString(g2, value, x + 58, y, width - 58, true);
        }

        private double overlayRenderScale() {
            if (!overlayOnly || overlayZoomFactor <= 1.0) {
                return 1.0;
            }
            return Math.min(overlayZoomFactor, 3.0);
        }

        private double overlayDisplayScale() {
            return overlayZoomFactor / overlayRenderScale();
        }

        private List<OverlayAxisControlLayout> buildOverlayAxisControls(int x, int y, int width, int rowHeight) {
            List<OverlayAxisControlLayout> controls = new ArrayList<>(5);
            controls.add(createOverlayAxisControl("X", itemPreviewRotationXSlider, x, y, width, rowHeight));
            controls.add(createOverlayAxisControl("Y", itemPreviewRotationYSlider, x, y + rowHeight + 8, width, rowHeight));
            controls.add(createOverlayAxisControl("Z", itemPreviewRotationZSlider, x, y + ((rowHeight + 8) * 2), width, rowHeight));
            controls.add(createOverlayAxisControl("MX", itemPreviewOffsetXSlider, x, y + ((rowHeight + 8) * 3), width, rowHeight));
            controls.add(createOverlayAxisControl("MY", itemPreviewOffsetYSlider, x, y + ((rowHeight + 8) * 4), width, rowHeight));
            return controls;
        }

        private OverlayAxisControlLayout createOverlayAxisControl(String label, JSlider slider, int x, int y, int width, int height) {
            int labelWidth = 18;
            int buttonSize = 18;
            int gap = 8;
            int valueWidth = 44;
            int trackX = x + labelWidth + buttonSize + (gap * 2);
            int trackW = Math.max(48, width - labelWidth - valueWidth - (buttonSize * 2) - (gap * 4));
            Rectangle minusBounds = new Rectangle(x + labelWidth + gap, y + 3, buttonSize, buttonSize);
            Rectangle trackBounds = new Rectangle(trackX, y + 7, trackW, 8);
            Rectangle plusBounds = new Rectangle(trackX + trackW + gap, y + 3, buttonSize, buttonSize);
            Rectangle valueBounds = new Rectangle(plusBounds.x + buttonSize + gap, y, valueWidth, height);
            return new OverlayAxisControlLayout(label, slider, minusBounds, plusBounds, trackBounds, valueBounds);
        }

        private void drawOverlayAxisControl(Graphics2D g2, OverlayAxisControlLayout control) {
            Font baseFont = g2.getFont();
            g2.setFont(baseFont.deriveFont(Font.BOLD, 12f));
            g2.setColor(TEXT);
            g2.drawString(control.label(), control.minusBounds().x - 24, control.minusBounds().y + 13);

            drawOverlayPillButton(g2, control.minusBounds(), "-");
            drawOverlayPillButton(g2, control.plusBounds(), "+");

            Rectangle track = control.trackBounds();
            g2.setColor(new Color(72, 72, 72));
            g2.fillRoundRect(track.x, track.y, track.width, track.height, 999, 999);
            double ratio = (control.slider().getValue() - control.slider().getMinimum()) / (double) Math.max(1, control.slider().getMaximum() - control.slider().getMinimum());
            int filledWidth = Math.max(8, (int) Math.round(track.width * ratio));
            g2.setColor(new Color(220, 138, 0, 210));
            g2.fillRoundRect(track.x, track.y, filledWidth, track.height, 999, 999);
            int knobX = track.x + (int) Math.round(track.width * ratio);
            g2.setColor(Color.WHITE);
            g2.fillOval(knobX - 5, track.y - 4, 10, 16);

            g2.setFont(baseFont.deriveFont(Font.PLAIN, 11f));
            g2.setColor(TEXT);
            String valueText = String.valueOf(control.slider().getValue());
            drawClippedString(g2, valueText, control.valueBounds().x, control.valueBounds().y + 16, control.valueBounds().width, true);
            g2.setFont(baseFont);
        }

        private void drawOverlayPillButton(Graphics2D g2, Rectangle bounds, String text) {
            g2.setColor(new Color(77, 77, 77, 235));
            g2.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 10, 10);
            g2.setColor(new Color(110, 110, 110));
            g2.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 10, 10);
            g2.setColor(Color.WHITE);
            FontMetrics metrics = g2.getFontMetrics();
            int textX = bounds.x + Math.max(0, (bounds.width - metrics.stringWidth(text)) / 2);
            int textY = bounds.y + ((bounds.height - metrics.getHeight()) / 2) + metrics.getAscent();
            g2.drawString(text, textX, textY);
        }

        private void drawLoadingOrb(Graphics2D g2, int centerX, int centerY) {
            long tick = System.currentTimeMillis() / 120L;
            for (int i = 0; i < 8; i++) {
                double angle = ((tick + i) % 8) * (Math.PI * 2.0 / 8.0);
                int x = centerX + (int) Math.round(Math.cos(angle) * 14);
                int y = centerY + (int) Math.round(Math.sin(angle) * 14);
                int alpha = 40 + ((i + 1) * 24);
                g2.setColor(new Color(220, 138, 0, Math.min(255, alpha)));
                g2.fillOval(x - 3, y - 3, 6, 6);
            }
        }

        private void drawClippedString(Graphics2D g2, String text, int x, int y, int width, boolean alignLeft) {
            if (text == null || width <= 0) {
                return;
            }
            FontMetrics metrics = g2.getFontMetrics();
            String clipped = text;
            if (metrics.stringWidth(clipped) > width) {
                String ellipsis = "...";
                int ellipsisWidth = metrics.stringWidth(ellipsis);
                if (ellipsisWidth >= width) {
                    clipped = ellipsis;
                } else {
                    int maxChars = clipped.length();
                    while (maxChars > 0) {
                        String candidate = clipped.substring(0, maxChars) + ellipsis;
                        if (metrics.stringWidth(candidate) <= width) {
                            clipped = candidate;
                            break;
                        }
                        maxChars--;
                    }
                    if (maxChars == 0) {
                        clipped = ellipsis;
                    }
                }
            }
            int drawX = alignLeft ? x : x + Math.max(0, (width - metrics.stringWidth(clipped)) / 2);
            g2.drawString(clipped, drawX, y);
        }

        private boolean hasRenderFailure() {
            return renderedFailure != null;
        }

        private boolean hasRenderedImage() {
            return renderedImage != null;
        }

        private int applyPreviewZoomBoost(int zoom) {
            return (int) Math.round(zoom * PREVIEW_ZOOM_MULTIPLIER);
        }

        private record PreviewRenderKey(int itemId, ItemPreviewMode mode, int inventoryModelId, int maleModelId1, int maleModelId2, int maleModelId3, int femaleModelId1, int femaleModelId2, int femaleModelId3, int zoom, int rotationX, int rotationY, int rotationZ, int offsetX, int offsetY, int animationFrame, int width, int height) {
        }

        private record PreviewRenderResult(BufferedImage image, String failure) {
        }

        private record OverlayAxisControlLayout(String label, JSlider slider, Rectangle minusBounds, Rectangle plusBounds, Rectangle trackBounds, Rectangle valueBounds) {
        }
    }

    private enum ItemPreviewMode {
        INVENTORY("Inventory"),
        MALE("Male"),
        FEMALE("Female");

        private final String label;

        ItemPreviewMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}

















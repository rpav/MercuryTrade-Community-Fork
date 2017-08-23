package com.mercury.platform.ui.frame.movable;

import com.mercury.platform.core.ProdStarter;
import com.mercury.platform.shared.FrameVisibleState;
import com.mercury.platform.shared.config.Configuration;
import com.mercury.platform.shared.config.configration.PlainConfigurationService;
import com.mercury.platform.shared.config.descriptor.NotificationSettingsDescriptor;
import com.mercury.platform.shared.entity.message.FlowDirections;
import com.mercury.platform.shared.entity.message.NotificationType;
import com.mercury.platform.shared.store.MercuryStoreCore;
import com.mercury.platform.ui.components.ComponentsFactory;
import com.mercury.platform.ui.components.fields.font.FontStyle;
import com.mercury.platform.ui.components.fields.font.TextAlignment;
import com.mercury.platform.ui.components.panel.notification.NotificationPanel;
import com.mercury.platform.ui.components.panel.notification.controller.stub.IncStubController;
import com.mercury.platform.ui.components.panel.notification.controller.stub.OutStubController;
import com.mercury.platform.ui.components.panel.notification.factory.NotificationPanelFactory;
import com.mercury.platform.ui.frame.titled.TestEngine;
import com.mercury.platform.ui.misc.AppThemeColor;
import com.mercury.platform.ui.misc.MercuryStoreUI;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class NotificationFrame extends AbstractMovableComponentFrame {
    private List<NotificationPanel> notificationPanels;
    private List<String> currentOffers;
    private PlainConfigurationService<NotificationSettingsDescriptor> config;
    private NotificationPanelFactory providersFactory;
    private JPanel container;
    private JPanel expandPanel;
    private JPanel stubExpandPanel;
    private boolean expanded;

    @Override
    protected void initialize() {
        super.initialize();
        this.processSEResize = false;
        this.notificationPanels = new ArrayList<>();
        this.config = Configuration.get().notificationConfiguration();
        this.componentsFactory.setScale(this.scaleConfig.get("notification"));
        this.stubComponentsFactory.setScale(this.scaleConfig.get("notification"));
        this.providersFactory = new NotificationPanelFactory();
    }

    @Override
    public void onViewInit() {
        this.getRootPane().setBorder(null);
        this.setBackground(AppThemeColor.TRANSPARENT);
        this.currentOffers = new ArrayList<>();
        this.container = new JPanel();
        this.container.setBackground(AppThemeColor.TRANSPARENT);
        this.container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        this.expandPanel = this.getExpandPanel();
        this.stubExpandPanel = this.componentsFactory.getJPanel(new BorderLayout(), AppThemeColor.TRANSPARENT);
        this.stubExpandPanel.setPreferredSize(this.expandPanel.getPreferredSize());
        this.add(this.stubExpandPanel, BorderLayout.LINE_START);
        this.add(this.container, BorderLayout.CENTER);
        this.setVisible(true);
        this.pack();
    }

    @Override
    @SuppressWarnings("all")
    public void subscribe() {
        MercuryStoreCore.newNotificationSubject.subscribe(notification -> {
            SwingUtilities.invokeLater(() -> {
                NotificationPanel notificationPanel = this.providersFactory.getProviderFor(notification.getType())
                        .setData(notification)
                        .setComponentsFactory(this.componentsFactory)
                        .build();
                String message = StringUtils.substringAfter(notification.getSourceString(), ":");
                if (this.currentOffers.contains(message)) {
                    notificationPanel.setDuplicate(true);
                } else {
                    this.currentOffers.add(message);
                }
                this.addNotification(notificationPanel);
            });
        });
        MercuryStoreCore.newScannerMessageSubject.subscribe(message -> {
            SwingUtilities.invokeLater(() -> {
                NotificationPanel notificationPanel = this.providersFactory.getProviderFor(NotificationType.SCANNER_MESSAGE)
                        .setData(message)
                        .setComponentsFactory(this.componentsFactory)
                        .build();
                this.addNotification(notificationPanel);
                Timer packTimer = new Timer(5, action -> {
                    this.pack();
                    if (this.notificationPanels.size() > 1 && this.config.get().getFlowDirections().equals(FlowDirections.UPWARDS)) {
                        this.setLocation(new Point(this.getLocation().x, this.getLocation().y - notificationPanel.getSize().height));
                    }
                });
                packTimer.setRepeats(false);
                packTimer.start();
            });
        });
        MercuryStoreCore.removeNotificationSubject.subscribe(notification -> {
            SwingUtilities.invokeLater(() -> {
                NotificationPanel notificationPanel = this.notificationPanels.stream()
                        .filter(it -> it.getData().equals(notification))
                        .findAny().orElse(null);
                this.currentOffers.remove(StringUtils.substringAfter(notification.getSourceString(), ":"));
                this.removeNotification(notificationPanel);
            });
        });
        MercuryStoreCore.removeScannerNotificationSubject.subscribe(message -> {
            SwingUtilities.invokeLater(() -> {
                NotificationPanel notificationPanel = this.notificationPanels.stream()
                        .filter(it -> it.getData().equals(message))
                        .findAny().orElse(null);
                this.removeNotification(notificationPanel);
            });
        });
        MercuryStoreCore.hotKeySubject.subscribe(hotkeyDescriptor -> {
            SwingUtilities.invokeLater(() -> {
                if (this.notificationPanels.size() > 0 && ProdStarter.APP_STATUS.equals(FrameVisibleState.SHOW)) {
                    this.notificationPanels.get(0).onHotKeyPressed(hotkeyDescriptor);
                }
            });
        });
        MercuryStoreUI.settingsPostSubject.subscribe(state -> {
            if (this.config.get().getFlowDirections().equals(FlowDirections.DOWNWARDS)) {
                this.setLocation(this.framesConfig.get("NotificationFrame").getFrameLocation());
            }
        });
    }

    private void addNotification(NotificationPanel notificationPanel) {
        this.notificationPanels.add(notificationPanel);
        this.container.add(notificationPanel);
        if (this.notificationPanels.size() > this.config.get().getLimitCount()) {
            if (!this.expanded) {
                notificationPanel.setVisible(false);
            }
            this.remove(this.stubExpandPanel);
            this.add(this.expandPanel, BorderLayout.LINE_START);
        }
        this.pack();
        this.repaint();
        if (this.notificationPanels.size() > 1
                && this.config.get().getFlowDirections().equals(FlowDirections.UPWARDS)) {
            this.setLocation(new Point(this.getLocation().x, this.getLocation().y - notificationPanel.getSize().height));
        }
    }

    private void removeNotification(NotificationPanel notificationPanel) {
        notificationPanel.onViewDestroy();
        int limitCount = this.config.get().getLimitCount();
        if (!this.expanded && this.notificationPanels.size() > limitCount) {
            this.notificationPanels.get(limitCount).setVisible(true);
        }
        this.container.remove(notificationPanel);
        this.notificationPanels.remove(notificationPanel);
        if (this.notificationPanels.size() - 1 < this.config.get().getLimitCount()) {
            this.remove(this.expandPanel);
            this.add(this.stubExpandPanel, BorderLayout.LINE_START);
        }
        this.pack();
        this.repaint();
        if (this.config.get().getFlowDirections().equals(FlowDirections.UPWARDS)
                && this.notificationPanels.size() == 0) {
            this.setLocation(this.framesConfig.get("NotificationFrame").getFrameLocation());
        }
    }

    @Override
    protected void onScaleLock() {
        JPanel var = this.expandPanel;
        this.expandPanel = this.getExpandPanel();
        this.stubExpandPanel.setPreferredSize(this.expandPanel.getPreferredSize());
        if (this.notificationPanels.size() > this.config.get().getLimitCount()) {
            this.remove(var);
            this.add(this.expandPanel, BorderLayout.LINE_START);
        }
        super.onScaleLock();
    }

    @Override
    protected JPanel getPanelForPINSettings() {
        JPanel root = this.componentsFactory.getJPanel(new BorderLayout(), AppThemeColor.FRAME);
        JPanel panel = this.componentsFactory.getJPanel(new BorderLayout(), AppThemeColor.FRAME);
        panel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, AppThemeColor.BORDER));
        JLabel textLabel = this.componentsFactory.getTextLabel(FontStyle.BOLD, AppThemeColor.TEXT_DEFAULT, TextAlignment.CENTER, 22f, "Notification panel");
        textLabel.setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(textLabel, BorderLayout.CENTER);

        JPanel limitPanel = this.componentsFactory.getJPanel(new BorderLayout(), AppThemeColor.FRAME);
        limitPanel.setPreferredSize(this.expandPanel.getPreferredSize());
        JLabel expandIconLabel = componentsFactory.getIconLabel("app/expand_button_pin.png", 24, SwingConstants.CENTER);
        limitPanel.add(expandIconLabel, BorderLayout.CENTER);
        root.add(panel, BorderLayout.CENTER);
        root.add(limitPanel, BorderLayout.LINE_START);
        root.setPreferredSize(new Dimension((int) (400 * componentsFactory.getScale()), (int) (110 * componentsFactory.getScale())));
        return root;
    }

    @Override
    protected void registerDirectScaleHandler() {
        MercuryStoreUI.notificationScaleSubject.subscribe(this::changeScale);
    }

    @Override
    protected void performScaling(Map<String, Float> scaleData) {
        this.componentsFactory.setScale(scaleData.get("notification"));
        this.notificationPanels.forEach(it -> {
            it.setComponentsFactory(this.componentsFactory);
        });
        this.pack();
        this.repaint();
    }

    @Override
    @SuppressWarnings("all")
    protected JPanel defaultView(ComponentsFactory factory) {
        TestEngine testEngine = new TestEngine();
        JPanel root = factory.getJPanel(new BorderLayout());
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));

        root.add(this.providersFactory
                .getProviderFor(NotificationType.INC_ITEM_MESSAGE)
                .setData(testEngine.getRandomItemIncMessage())
                .setComponentsFactory(factory)
                .setController(new IncStubController())
                .build());
        root.add(this.providersFactory
                .getProviderFor(NotificationType.OUT_ITEM_MESSAGE)
                .setData(testEngine.getRandomItemOutMessage())
                .setComponentsFactory(factory)
                .setController(new OutStubController())
                .build());
        return root;
    }

    @Override
    protected LayoutManager getFrameLayout() {
        return new BorderLayout();
    }

    private JPanel getExpandPanel() {
        JPanel root = this.componentsFactory.getJPanel(new BorderLayout());
        root.setBackground(AppThemeColor.MSG_HEADER);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 1, 0, AppThemeColor.FRAME),
                BorderFactory.createMatteBorder(1, 1, 1, 1, AppThemeColor.MSG_HEADER_BORDER)));
        String iconPath = "app/collapse-all.png";
        JButton expandButton = componentsFactory.getIconButton(iconPath, 22, AppThemeColor.MSG_HEADER, "");
        expandButton.addActionListener(action -> {
            if (this.expanded) {
                expandButton.setIcon(this.componentsFactory.getIcon("app/collapse-all.png", 22));
                this.notificationPanels
                        .stream()
                        .skip(this.config.get().getLimitCount())
                        .forEach(it -> it.setVisible(false));
            } else {
                expandButton.setIcon(this.componentsFactory.getIcon("app/expand-all.png", 22));
                this.notificationPanels.forEach(it -> {
                    if (!it.isVisible()) {
                        it.setVisible(true);
                    }
                });
            }
            this.expanded = !this.expanded;
            this.pack();
            this.repaint();
        });
        expandButton.setAlignmentY(SwingConstants.CENTER);
        root.add(expandButton, BorderLayout.CENTER);
        return root;
    }
}
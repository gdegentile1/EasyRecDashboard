package com.finboxsolutions.easyrec.dashboard;

import com.finboxsolutions.easyrec.dashboard.dao.DashboardDao;
import com.finboxsolutions.easyrec.dashboard.dao.JdbcDashboardDao;
import com.finboxsolutions.easyrec.dashboard.ui.DashboardPanel;

import javax.sql.DataSource;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * How to put the dashboard into EasyRec.
 *
 * <p>The module is one panel with one dependency, a {@link DataSource} pointing at the
 * database EasyRec already writes its statistics to. It installs no look and feel of its
 * own and reads every colour through UIManager, so it inherits whatever FlatLaf theme the
 * host has set.
 */
public final class DashboardLauncher {

    /**
     * The tab title, so the caller can find an already-open tab rather than adding a second.
     *
     * <p>The datasource alias itself is not declared here: EasyRec already names that
     * property as {@code PropertiesBean.PROPERTY_DASHBOARD_ALIAS} and surfaces it as
     * {@code ExecutionContextBean.getKpiAlias()}. One name for one thing.
     */
    public static final String TAB_TITLE = "Dashboard";

    private DashboardLauncher() {
    }

    /**
     * The dashboard as a component, ready to add to a tab of the EasyRec window.
     *
     * <p>Build it on the EDT. It loads its first screen asynchronously, so adding it does
     * not block the caller.
     */
    public static JComponent createPanel(DataSource dataSource) {
        DashboardDao dao = new JdbcDashboardDao(dataSource);
        return new DashboardPanel(dao);
    }

    /** Opens the dashboard in a window of its own, for development and for a detached view. */
    public static JFrame openWindow(DataSource dataSource) {
        assert SwingUtilities.isEventDispatchThread() : "build Swing components on the EDT";
        JFrame frame = new JFrame("EasyRec Dashboard");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.getContentPane().add(createPanel(dataSource), BorderLayout.CENTER);
        frame.setPreferredSize(new Dimension(1440, 900));
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        return frame;
    }
}

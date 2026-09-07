import com.finboxsolutions.easyrec.dashboard.dao.DashboardDao;
import com.finboxsolutions.easyrec.dashboard.dao.JdbcDashboardDao;
import com.finboxsolutions.easyrec.dashboard.model.PivotMetric;
import com.finboxsolutions.easyrec.dashboard.model.PivotRow;
import com.finboxsolutions.easyrec.dashboard.service.PivotTreeBuilder;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/** Exercises the folding table model against a real pivot tree from the sample data. */
public final class PivotFoldTest {

    public static void main(String[] args) throws Exception {
        String url = "jdbc:sqlite:" + args[0];
        DataSource ds = new DataSource() {
            @Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(url); }
            @Override public Connection getConnection(String u, String p) throws SQLException { return getConnection(); }
            @Override public PrintWriter getLogWriter() { return null; }
            @Override public void setLogWriter(PrintWriter out) { }
            @Override public void setLoginTimeout(int s) { }
            @Override public int getLoginTimeout() { return 0; }
            @Override public Logger getParentLogger() { return Logger.getGlobal(); }
            @Override public <T> T unwrap(Class<T> i) { return null; }
            @Override public boolean isWrapperFor(Class<?> i) { return false; }
        };
        DashboardDao dao = new JdbcDashboardDao(ds);

        int runId = Integer.parseInt(args[1]);
        int templateId = Integer.parseInt(args[2]);
        List<PivotRow> pivots = dao.findPivots(runId, templateId);
        PivotTreeBuilder.Tree tree = PivotTreeBuilder.build(pivots, "[==BREAK_TYPE==, M_TP_PFOLIO]");

        int failures = 0;
        System.out.printf(Locale.ROOT, "tree nodes=%d roots=%d maxDepth=%d%n",
                tree.rows().size(), tree.roots().size(), tree.maxDepth());
        int nodesWithChildren = 0;
        for (PivotTreeBuilder.Node node : tree.rows()) {
            if (node.hasChildren()) {
                nodesWithChildren++;
            }
        }
        failures += check(tree.rows().size() >= tree.roots().size(),
                "every root appears in the flattened tree");
        failures += check(tree.levels().size() == tree.maxDepth() + 1,
                "one level entry per depth the tree reaches");

        // A parent's Total Breaks must equal the sum of its children's, since the tree was
        // folded from leaves that EasyRec stored individually.
        double rootSum = 0.0d;
        for (PivotTreeBuilder.Node node : tree.rows()) {
            if (node.depth() == 0) {
                rootSum += node.value(PivotMetric.TOTAL_BREAKS);
            }
        }
        double leafSum = 0.0d;
        for (PivotTreeBuilder.Node node : tree.rows()) {
            if (!node.hasChildren()) {
                leafSum += node.value(PivotMetric.TOTAL_BREAKS);
            }
        }
        System.out.printf(Locale.ROOT, "roots total=%.2f leaves total=%.2f%n", rootSum, leafSum);
        failures += check(Math.abs(rootSum - leafSum) < 1e-6, "root totals equal leaf totals");

        System.out.println(failures == 0 ? "ALL CHECKS PASSED" : failures + " CHECKS FAILED");
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static int check(boolean condition, String what) {
        System.out.println((condition ? "  ok   " : "  FAIL ") + what);
        return condition ? 0 : 1;
    }
}

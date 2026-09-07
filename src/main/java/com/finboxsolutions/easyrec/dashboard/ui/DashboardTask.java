package com.finboxsolutions.easyrec.dashboard.ui;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.awt.Cursor;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs one dashboard query off the EDT and hands the result back on it.
 *
 * <p>Every screen here loads through this. The queries are not fast enough to run inline:
 * a comparison of six batches touches four tables, and a pivot tree reads the whole
 * breakdown for a reconciliation. Freezing the EasyRec window while that happens would be
 * the first thing anyone noticed.
 */
public final class DashboardTask<T> extends SwingWorker<T, Void> {

    private static final Logger LOGGER = Logger.getLogger(DashboardTask.class.getName());

    private final Component owner;
    private final String description;
    private final Supplier<T> work;
    private final Consumer<T> onSuccess;

    private DashboardTask(Component owner, String description, Supplier<T> work,
                          Consumer<T> onSuccess) {
        this.owner = owner;
        this.description = description;
        this.work = work;
        this.onSuccess = onSuccess;
    }

    /**
     * @param owner       shown busy while the work runs, and the parent of any error dialog
     * @param description what failed, used in the log and the dialog
     */
    public static <T> void run(Component owner, String description, Supplier<T> work,
                               Consumer<T> onSuccess) {
        assert SwingUtilities.isEventDispatchThread() : "start tasks from the EDT";
        owner.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new DashboardTask<>(owner, description, work, onSuccess).execute();
    }

    @Override
    protected T doInBackground() {
        return work.get();
    }

    @Override
    protected void done() {
        owner.setCursor(Cursor.getDefaultCursor());
        try {
            onSuccess.accept(get());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException failure) {
            // A failed load leaves the previous screen on show rather than a blank one, so
            // the dialog is the only signal; it has to name what was being loaded.
            report("could not be loaded", failure.getCause());
        } catch (RuntimeException failure) {
            // The callback runs on the EDT, so anything it throws would otherwise escape
            // into the event pump: printed to the console, invisible in the application,
            // and leaving a half-built screen behind. Rendering failures deserve the same
            // dialog as query failures.
            report("could not be displayed", failure);
        }
    }

    private void report(String what, Throwable failure) {
        LOGGER.log(Level.SEVERE, "Dashboard: " + description + " " + what, failure);
        JOptionPane.showMessageDialog(owner,
                description + " " + what + ".\n" + rootMessage(failure),
                "EasyRec Dashboard", JOptionPane.ERROR_MESSAGE);
    }

    private static String rootMessage(Throwable failure) {
        if (failure == null) {
            return "Unknown cause";
        }
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null ? current.getClass().getSimpleName() : message;
    }
}
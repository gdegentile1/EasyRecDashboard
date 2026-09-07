package com.finboxsolutions.easyrec.dashboard.ui.component;

import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/**
 * One field of a header that can be written back, armed by a pencil.
 *
 * <p>The dashboard is a reading surface and these are the only places on it that write, so a
 * field does not sit there editable waiting to catch a stray keystroke: the pencil arms it,
 * the tick commits, the cross puts back what was there, Enter is the tick and Escape the
 * cross. That is how EasyRec's own option panels edit a single field - see
 * {@code DefaultOptionPanel.createEditButton}.
 *
 * <p>The saving is the caller's, because it has to happen off the EDT and this class knows
 * nothing about databases. {@code onSave} is handed the edited text; the caller persists it
 * and calls {@link #saved} when the write has returned. Until it does, the field stays armed
 * and holding what was typed - a failed save should leave the operator looking at their edit,
 * not at the old value with the edit silently gone.
 */
public class EditableField extends JPanel {

    private static final long serialVersionUID = 1L;

    /**
     * A floor under the label column so two of these stack with their fields in line, and a
     * cap on the field column so the buttons stay against the field rather than following an
     * empty cell out to the right.
     */
    private static final String COLUMNS = "[72::]6[::760,grow,fill]2[]0[]0[]push";

    private final JTextField field;
    private final JButton editButton;
    private final JButton saveButton;
    private final JButton cancelButton;
    private final transient Consumer<String> onSave;

    private String stored = "";

    /**
     * @param label       the field's name, set beside it
     * @param placeholder shown when the value is empty
     * @param onSave      given the edited text when the tick is pressed
     */
    public EditableField(String label, String placeholder, Consumer<String> onSave) {
        super(new MigLayout("insets 0, fillx", COLUMNS, "[]"));
        setOpaque(false);
        this.onSave = onSave;

        field = new JTextField();
        // A placeholder rather than a HintTextField: that class reports an empty string from
        // getText whenever the text happens to equal its hint, so a value that reads like the
        // prompt would be saved back as nothing.
        field.putClientProperty("JTextField.placeholderText", placeholder);
        field.addActionListener(event -> commit());
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    cancel();
                }
            }
        });

        editButton = Sections.createIconButton(DashboardIcons.ICON_EDIT,
                "Edit the " + label.toLowerCase(java.util.Locale.ROOT), event -> begin());
        saveButton = Sections.createIconButton(DashboardIcons.ICON_SAVE,
                "Save the " + label.toLowerCase(java.util.Locale.ROOT), event -> commit());
        cancelButton = Sections.createIconButton(DashboardIcons.ICON_CANCEL,
                "Discard the change", event -> cancel());

        add(Sections.createFieldLabel(label));
        add(field, "growx");
        add(editButton);
        add(saveButton);
        add(cancelButton);

        setEditing(false);
    }

    /** Loads a value and returns the field to read-only. */
    public void show(String value) {
        stored = value == null ? "" : value.trim();
        field.setText(stored);
        setEditing(false);
    }

    /** Confirms a write: the value becomes what the cross would put back. */
    public void saved(String value) {
        stored = value == null ? "" : value.trim();
        field.setText(stored);
        setEditing(false);
    }

    /** The value last loaded or saved. */
    public String stored() {
        return stored;
    }

    /** Whether the pencil is offered at all. */
    public void setEditingAllowed(boolean allowed) {
        editButton.setEnabled(allowed);
    }

    public void setToolTipText(String tooltip, String fieldTooltip) {
        super.setToolTipText(tooltip);
        field.setToolTipText(fieldTooltip);
    }

    private void begin() {
        setEditing(true);
        field.requestFocusInWindow();
        field.selectAll();
    }

    private void cancel() {
        field.setText(stored);
        setEditing(false);
    }

    private void commit() {
        if (!field.isEditable()) {
            return;
        }
        String edited = field.getText().trim();
        if (edited.equals(stored)) {
            setEditing(false);
            return;
        }
        onSave.accept(edited);
    }

    /** Which of the three buttons are on show, and whether the field takes typing. */
    private void setEditing(boolean editing) {
        field.setEditable(editing);
        field.setFocusable(editing);
        editButton.setVisible(!editing);
        saveButton.setVisible(editing);
        cancelButton.setVisible(editing);
    }
}

package com.finboxsolutions.easyrec.dashboard.ui.pivot;

import java.awt.Component;

import javax.swing.table.TableCellRenderer;

import com.finboxsolutions.easyrec.dashboard.ui.component.Fonts;
import com.finboxsolutions.swing.treetable.ExcelTreeTable;
import com.finboxsolutions.swing.treetable.model.FilterableExcelTreeTableModel;

/**
 * EasyRec's pivot tree table, sized like the rest of the dashboard.
 *
 * <p>The one addition is the type size. The breakdown sits directly under the column
 * statistics on the reconciliation screen, and both are drawn by the same grid - so if the
 * zoom wrapper decides the size for one and not the other, the two halves of one screen end
 * up several points apart. {@code DashboardTable} settles it the same way, for the same
 * reason; see {@link Fonts#sized}.
 */
public class DashboardPivotTable extends ExcelTreeTable {

    private static final long serialVersionUID = 1L;

    public DashboardPivotTable(FilterableExcelTreeTableModel model) {
        super(model);
    }

    @Override
    public Component prepareRenderer(TableCellRenderer renderer, int row, int column) {
        Component component = super.prepareRenderer(renderer, row, column);
        component.setFont(Fonts.sized(component.getFont(), Fonts.cellSize(), getRowHeight()));
        return component;
    }
}

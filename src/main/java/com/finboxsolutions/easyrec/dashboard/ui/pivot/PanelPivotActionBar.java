package com.finboxsolutions.easyrec.dashboard.ui.pivot;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.jdesktop.swingx.JXButton;

import com.finboxsolutions.common.gui.fonticons.builder.AwesomeFactory;
import com.finboxsolutions.common.gui.fonticons.builder.GoogleFactory;
import com.finboxsolutions.common.gui.fonticons.builder.GoogleOutlineFactory;
import com.finboxsolutions.common.gui.fonticons.button.FontIconButton;
import com.finboxsolutions.common.gui.fonticons.symbols.AwesomeUnicodeConstants;
import com.finboxsolutions.common.gui.fonticons.symbols.GoogleUnicodeConstants;
import com.finboxsolutions.common.gui.icon.IconLoader;
import com.finboxsolutions.common.gui.text.HintTextField;
import com.finboxsolutions.common.gui.utils.JSearchTextField;
import com.finboxsolutions.easyrec.dashboard.ui.GuiPreferences;

import net.miginfocom.swing.MigLayout;

@SuppressWarnings({"squid:ClassVariableVisibilityCheck", "squid:S1444"})	// "public static" fields should be constant
public class PanelPivotActionBar extends JPanel {
	private static final long serialVersionUID = -8328000594462632343L;

	private static final org.apache.logging.log4j.Logger log = org.apache.logging.log4j.LogManager.getLogger(PanelPivotActionBar.class);

	private static final int FONT_SIZE = 20;
	
	private static final Color RED = new Color(204,51,0);
	//private static final Color GREEN =  new Color(51,153,0);
	// private static final Color YELLOW = new Color(255,200,0);
	private static final Color BLUE = new Color(0,102,204);
	
	public static Icon ICON_VIEW_PCT 	= null;
	public static Icon ICON_VIEW_RLT 	= null;
	public static Icon ICON_BREAK 		= null;
	
	public static final String COMMAND_EDIT_PIVOT	= "EditPivot";
	public static final String COMMAND_INCLUDE_PIVOT= "IncludePivot";
	public static final String COMMAND_LEVEL1		= "Level1";
	public static final String COMMAND_LEVEL2		= "Level2";
	public static final String COMMAND_LEVEL3		= "Level3";
	public static final String COMMAND_LEVEL4		= "Level4";
	public static final String COMMAND_LEVEL5		= "Level5";
	public static final String COMMAND_LEVEL6		= "Level6";
	public static final String COMMAND_EXPAND		= "Expand";
	public static final String COMMAND_COLLAPSE		= "Collapse";
	public static final String COMMAND_TOLERANCE	= "Apply Tolerances";
	public static final String COMMAND_SEARCH		= "Search";
	public static final String COMMAND_VIEW_PCT		= "View Percent";
	public static final String COMMAND_VIEW_RLT		= "View Relative";
	public static final String COMMAND_BREAK		= "Break";
	public static final String COMMAND_EXCEL		= "Excel Report";
	public static final String COMMAND_TOTAL		= "Total";
	public static final String COMMAND_REFRESH		= "Refresh";
	public static final String COMMAND_CLEAR_FILTERS= "Clear Filters";
	
	private JButton[]  buttonLevels;
	private JButton  buttonLevel1;
	private JButton  buttonLevel2;
	private JButton  buttonLevel3;
	private JButton  buttonLevel4;
	private JButton  buttonLevel5;
	private JButton  buttonLevel6;
	private JButton  buttonExpandAll;
	private JButton  buttonCollapseAll;

	private JButton  buttonViewPercent;
	private JButton  buttonViewRelative;

	private FontIconButton buttonViewBreak;
	private FontIconButton buttonEditPivot;
	private JButton buttonIncludePivot;
	private FontIconButton buttonRefresh;
	private FontIconButton buttonSubTotals;
	private FontIconButton buttonExcelReport;
	private FontIconButton buttonClearFilters;

	private JSearchTextField searchTextField;
	private HintTextField filterTextField;
	private JCheckBox checkboxApplyTolerances;
	
	private transient ActionListener actionListener;

	static {
		ICON_VIEW_PCT 	= IconLoader.loadIconImage(PanelPivotActionBar.class, "resources/img/rulerPercent.png");
		ICON_VIEW_RLT 	= IconLoader.loadIconImage(PanelPivotActionBar.class, "resources/img/rulerReal.png");
		ICON_BREAK 		= IconLoader.loadIconImage(PanelPivotActionBar.class, "resources/img/search2_16.png");
	}

	/**
	 * Constructor
	 * @param action listener
	 */
	public PanelPivotActionBar(ActionListener actionListener) {
		super();

		//this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
		this.setLayout(new MigLayout("insets -2 -5 0 -5")); // T, L, B, R.
		this.actionListener = actionListener;

		buttonViewPercent  = buildIconButton(""	, "View Percent"	, COMMAND_VIEW_PCT	, ICON_VIEW_PCT);
		buttonViewRelative = buildIconButton(""	, "View Relative"	, COMMAND_VIEW_RLT	, ICON_VIEW_RLT);
		//buttonViewBreak	   = buildIconButton("Breaks", "Show Breaks"	, COMMAND_BREAK		, ICON_BREAK);
		
		buttonEditPivot   = buildFontIconButton(AwesomeUnicodeConstants.FA_EDIT, GuiPreferences.DARK_GRAY, "Edit Pivot", COMMAND_EDIT_PIVOT);
		buttonIncludePivot = AwesomeFactory.getInstance().buildButton(AwesomeUnicodeConstants.FA_COG, 16, GuiPreferences.COLOR_BLUE, "Edit Columns to include in Pivot", COMMAND_INCLUDE_PIVOT);
		// buttonIncludePivot= buildFontIconButton(AwesomeUnicodeConstants.FA_COG, GuiPreferences.DARK_GRAY, "", COMMAND_INCLUDE_PIVOT);
		buttonRefresh     = buildFontIconButton(AwesomeUnicodeConstants.FA_REFRESH, BLUE, "Refresh", COMMAND_REFRESH);
		buttonSubTotals   = buildFontIconButton(AwesomeUnicodeConstants.FA_TOGGLE_ON, GuiPreferences.DARK_GRAY, "Sub-totals", COMMAND_TOTAL);
		buttonExcelReport = buildFontIconButton(AwesomeUnicodeConstants.FA_FILE_EXCEL_O, GuiPreferences.COLOR_GREEN_EXCEL, "Export to Excel", COMMAND_EXCEL);
		buttonViewBreak   = buildFontIconButton(AwesomeUnicodeConstants.FA_SEARCH, GuiPreferences.COLOR_BLUE, "Show Breaks", COMMAND_BREAK);
		
		GoogleOutlineFactory factoryOutline = GoogleOutlineFactory.getInstance();
		buttonClearFilters = factoryOutline.buildFontIconButton(GoogleUnicodeConstants.FILTER_OUT_OFF, FONT_SIZE, RED, "Clear Filters", FontIconButton.HORIZONTAL_ALIGNMENT);
		buttonClearFilters.setActionCommand(COMMAND_CLEAR_FILTERS);
		buttonClearFilters.setBorder(BorderFactory.createEmptyBorder());
    	
    	buttonExpandAll	  = factoryOutline.buildButton(GoogleUnicodeConstants.ADD_BOX, FONT_SIZE, null, "Expand All", COMMAND_EXPAND);
		buttonCollapseAll = factoryOutline.buildButton(GoogleUnicodeConstants.INDETERMINATE_CHECK_BOX, FONT_SIZE, null, "Collapse All", COMMAND_COLLAPSE);
		buttonLevel1      = factoryOutline.buildButton(GoogleUnicodeConstants.LOOKS_ONE, FONT_SIZE, null, "Show level 1"	, COMMAND_LEVEL1);
		buttonLevel2      = factoryOutline.buildButton(GoogleUnicodeConstants.LOOKS_TWO, FONT_SIZE, null, "Show level 2"	, COMMAND_LEVEL2);
		buttonLevel3      = factoryOutline.buildButton(GoogleUnicodeConstants.LOOKS_3, FONT_SIZE, null, "Show level 3"	, COMMAND_LEVEL3);
		buttonLevel4      = factoryOutline.buildButton(GoogleUnicodeConstants.LOOKS_4, FONT_SIZE, null, "Show level 4"	, COMMAND_LEVEL4);
		buttonLevel5      = factoryOutline.buildButton(GoogleUnicodeConstants.LOOKS_5, FONT_SIZE, null, "Show level 5"	, COMMAND_LEVEL5);
		buttonLevel6      = factoryOutline.buildButton(GoogleUnicodeConstants.LOOKS_6, FONT_SIZE, null, "Show level 6"	, COMMAND_LEVEL6);
		
		buttonLevels = new JButton[] {buttonLevel1, buttonLevel2, buttonLevel3, buttonLevel4, buttonLevel5, buttonLevel6};
		
		checkboxApplyTolerances = new JCheckBox("Apply Tolerances", true);
		checkboxApplyTolerances.setActionCommand(COMMAND_TOLERANCE);
		searchTextField = new JSearchTextField(25);
		filterTextField = new HintTextField("Min Impact", 9);
		
		this.checkboxApplyTolerances.addActionListener(actionListener);
		this.buttonLevel1.addActionListener(actionListener);
		this.buttonLevel2.addActionListener(actionListener);
		this.buttonLevel3.addActionListener(actionListener);
		this.buttonLevel4.addActionListener(actionListener);
		this.buttonLevel5.addActionListener(actionListener);
		this.buttonLevel6.addActionListener(actionListener);
		this.buttonExpandAll.addActionListener(actionListener);
		this.buttonCollapseAll.addActionListener(actionListener);
		this.buttonIncludePivot.addActionListener(actionListener);
		this.buttonEditPivot.addActionListener(actionListener);
		this.buttonViewPercent.addActionListener(actionListener);
		this.buttonViewRelative.addActionListener(actionListener);
		this.buttonViewBreak.addActionListener(actionListener);
		this.buttonExcelReport.addActionListener(actionListener);
		this.buttonSubTotals.addActionListener(actionListener);
		this.buttonRefresh.addActionListener(actionListener);
		this.buttonClearFilters.addActionListener(actionListener);
		
		this.add(searchTextField, "gapleft 3");
		this.add(buttonIncludePivot, "gapleft 3");
		this.add(buttonEditPivot, "gapleft 3");
		//this.add(buttonEditPivot);
		this.add(buttonRefresh, "gapleft 3");
		//this.add(buttonClearFilters, "gapleft 3");
		//this.add(buttonSubTotals);
		//this.add(getSeparator());
		//this.add(checkboxApplyTolerances);
		this.add(getSeparator());
		this.add(new JLabel("Level"));
		
		JPanel panelButtons = new JPanel(new GridBagLayout());
		panelButtons.add(buttonCollapseAll);
		panelButtons.add(buttonExpandAll);
		panelButtons.add(new JPanel());	// add separator
		panelButtons.add(buttonLevel1);
		panelButtons.add(buttonLevel2);
		panelButtons.add(buttonLevel3);
		panelButtons.add(buttonLevel4);
		panelButtons.add(buttonLevel5);
		panelButtons.add(buttonLevel6);
		this.add(panelButtons);
		
//		this.add(getSeparator());
//		this.add(new JLabel("View"));
//		this.add(buttonViewPercent);
//		this.add(buttonViewRelative);
		this.add(getSeparator());
		this.add(buttonSubTotals);
		this.add(buttonViewBreak, "gapleft 10");
		this.add(new JLabel(), "push, growx"); // This acts as the expandable space
		this.add(buttonExcelReport);
		
//		this.add(getSeparator());
//		this.add(new JLabel("Impact >"));
//		this.add(filterTextField);
	}
	
	private JComponent getSeparator() {
		JLabel separator = new JLabel(" | ");
		separator.setForeground(Color.LIGHT_GRAY);
		return separator;
	}
	
	/**
	 * Build button with icon
	 * @param label
	 * @param toolTip
	 * @param command
	 * @param icon
	 * @return
	 */
    private JXButton buildIconButton(String label, String toolTip, String command, Icon icon) {
    	JXButton button = new JXButton();
    	button.setIcon(icon);
    	//if(icon==null) {
    		button.setText(label);
    	//}
        button.setActionCommand(command);
        button.setToolTipText(toolTip);
        button.setBorderPainted(false);
        //button.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setPreferredSize(new Dimension(24,24));
        button.setContentAreaFilled(false);
        
        return button;
    }
    
	/**
	 * Build font icon button
	 * 
	 * @param unicode
	 * @param color
	 * @param label
	 * @param actionCommand
	 * @return
	 */
    private FontIconButton buildFontIconButton(char unicode, Color color, String label, String actionCommand) {
    	FontIconButton button = AwesomeFactory.getInstance().buildFontIconButton(unicode, 16, color, label, FontIconButton.HORIZONTAL_ALIGNMENT);
    	button.setActionCommand(actionCommand);
    	button.setBorder(BorderFactory.createEmptyBorder());
    	
    	return button;
    }

	public JCheckBox getCheckboxApplyTolerances() {
		return checkboxApplyTolerances;
	}

	public JSearchTextField getSearchTextField() {
		return searchTextField;
	}

	/**
	 * Set selected level indicator
	 * @param level
	 */
	public void setSelectedLevel(int level) {
		GoogleFactory factory = GoogleFactory.getInstance();
		GoogleOutlineFactory factoryOutline = GoogleOutlineFactory.getInstance();
		
		for (int i=0; i<buttonLevels.length; i++) {
			JButton button = buttonLevels[i];
			if((i+1)==level) {
				factory.applyFont(button, FONT_SIZE);
			} else {
				factoryOutline.applyFont(button, FONT_SIZE);
			}
		}
	}

	public void setSubTotals(boolean subTotal) {
		if(subTotal) {
			buttonSubTotals.getButton1().setText(String.valueOf(AwesomeUnicodeConstants.FA_TOGGLE_ON));
		} else {
			buttonSubTotals.getButton1().setText(String.valueOf(AwesomeUnicodeConstants.FA_TOGGLE_OFF));
		}
	}

	public FontIconButton getButtonSubTotals() {
		return buttonSubTotals;
	}

	public FontIconButton getButtonEditPivot() {
		return buttonEditPivot;
	}

	public FontIconButton getButtonExcelReport() {
		return buttonExcelReport;
	}

	public FontIconButton getButtonRefresh() {
		return buttonRefresh;
	}

	public FontIconButton getButtonViewBreak() {
		return buttonViewBreak;
	}

	public JButton getButtonIncludePivot() {
		return buttonIncludePivot;
	}

	public FontIconButton getButtonClearFilters() {
		return buttonClearFilters;
	}

}
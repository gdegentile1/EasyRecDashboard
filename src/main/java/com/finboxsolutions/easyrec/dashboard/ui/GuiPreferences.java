package com.finboxsolutions.easyrec.dashboard.ui;

import java.awt.Color;

import javax.swing.UIManager;

public class GuiPreferences {

	public static final Color COLOR_GREEN_EXCEL 		= new Color(19,129,71);
	public static final Color COLOR_RED_PPT 			= new Color(196,62,28);
	public static final Color COLOR_BLUE_WORD			= new Color(24, 90, 189);
	public static final Color COLOR_ORANGE_ZIP			= new Color(197, 122, 21);
	public static final Color COLOR_FOLDER				= new Color(255, 188, 31);
	
//	public static final Color COLOR_RED					= new Color(255, 51, 0);
//	public static final Color COLOR_BLUE				= new Color(111, 140, 177);
//	public static final Color COLOR_LIGHT_BLUE			= new Color(102, 172, 255, 156);
//	public static final Color COLOR_TITLES_BLUE 		= new Color(193, 209, 241);
//	public static final Color COLOR_ORANGE				= new Color(255, 204, 0); 	// Color.ORANGE;
//	public static final Color COLOR_GREEN				= new Color(60, 241, 0);
//	public static final Color COLOR_DARK_GREEN 			= new Color(122, 78, 51);	// use for Font in PPT report
//	public static final Color COLOR_LIGHT_GREEN			= new Color(143, 215, 62, 190); //new Color(151, 240, 151);	// new Color(204, 255, 204);
//	public static final Color COLOR_LIGHT_VIOLET		= new Color(188, 188, 222); // light violet
//	public static final Color COLOR_YELLOW				= new Color(255, 255, 0); 	// Color.YELLOW;
//	public static final Color COLOR_LIGHT_YELLOW		= new Color(250, 243, 221);
//	public static final Color COLOR_LIGHT_GRAY			= new Color(225, 225, 225); //new Color(204, 204, 204, 220); // Color.LIGHT_GRAY;
//	public static final Color COLOR_DESKTOP_BG			= new Color(70, 70, 70);
//	public static final Color COLOR_BALLOON_COMMENT_BG 	= new Color(255, 0, 0, 180);
//	public static final Color COLOR_TITLE_BORDER		= new Color(148, 145, 140);
//	public static final Color COLOR_GRAY_MENU_VERTICAL	= Color.GRAY;

	public static final Color COLOR_RED					= Color.decode("#FF3F00");
	public static final Color COLOR_LIGHT_RED			= Color.decode("#FF6633");
	public static final Color COLOR_BLUE				= Color.decode("#4C87C8"); 	// Color.decode("#6096BA");
	public static final Color COLOR_LIGHT_BLUE			= Color.decode("#A3CEF1");
//	public static final Color COLOR_TITLES_BLUE 		= ExcelColorPreferences.COLOR_TITLE; //Color.decode("#A4C3B2");
	public static final Color COLOR_ORANGE				= Color.decode("#FFCC00");
	public static final Color COLOR_DARK_ORANGE			= Color.decode("#FF9111");
	public static final Color COLOR_GREEN				= Color.decode("#8AC926");
	public static final Color COLOR_LIGHT_GREEN			= new Color(164, 229, 110, 200); // Color.decode("#A4E56E");
	public static final Color COLOR_PPT_REPORT_GREEN 	= new Color(122, 78, 51);	// use for Font in PPT report
	public static final Color COLOR_LIGHT_VIOLET		= Color.decode("#CBC0D3");
	public static final Color COLOR_YELLOW				= Color.decode("#FFFF44"); //Color.decode("#FFE66D");
	public static final Color COLOR_LIGHT_YELLOW		= Color.decode("#fefae0");
	public static final Color COLOR_LIGHT_PINK			= Color.decode("#FFF1E6");
	public static final Color COLOR_LIGHT_GRAY			= Color.decode("#E6E6E6");
	public static final Color COLOR_DESKTOP_BG			= new Color(70, 70, 70);
	public static final Color COLOR_BALLOON_COMMENT_BG 	= COLOR_RED;
	public static final Color COLOR_TITLE_BORDER		= Color.decode("#8EA096");
	public static final Color COLOR_GRAY_MENU_VERTICAL	= Color.decode("#8d99ae");
	
	public static final Color COLOR_DEFAULT_FOREGROUND 	= UIManager.getColor("TextField.foreground");
	public static final Color COLOR_DEFAULT_BACKGROUNG 	= UIManager.getColor("TextField.background");
	public static final Color DARK_GRAY 				= COLOR_DEFAULT_FOREGROUND;		// Color.DARK_GRAY; 

	/*
	 * Old colors
	 */
//	public static final Color COLOR_MATCH 				= Color.WHITE;
//	public static final Color COLOR_UNMATCH 			= COLOR_ORANGE;
//	public static final Color COLOR_MISSING_SOURCE 		= COLOR_YELLOW;
//	public static final Color COLOR_MISSING_TARGET 		= COLOR_GREEN;
//	public static final Color COLOR_IGNORE_COLUMN		= COLOR_LIGHT_GRAY;
//	public static final Color COLOR_MISSING_COLUMN 		= Color.PINK;
//	public static final Color COLOR_TOLERANCE			= COLOR_LIGHT_VIOLET;
//	public static final Color COLOR_FORCE_MATCH			= COLOR_LIGHT_GREEN;

	/*
	 *  New colors proposal
	 */
	public static final Color COLOR_MATCH 				= COLOR_DEFAULT_BACKGROUNG; 	// Color.WHITE;
	public static final Color COLOR_UNMATCH 			= COLOR_ORANGE;
	public static final Color COLOR_MISSING_SOURCE 		= COLOR_YELLOW;
	public static final Color COLOR_MISSING_TARGET 		= COLOR_LIGHT_BLUE; //COLOR_LIGHT_VIOLET;  //COLOR_GREEN;
	public static final Color COLOR_IGNORE_COLUMN		= Color.decode("#d5d8dc");	//COLOR_LIGHT_GRAY;	
	public static final Color COLOR_MISSING_COLUMN 		= COLOR_LIGHT_PINK; // Color.PINK;
	public static final Color COLOR_TOLERANCE			= COLOR_LIGHT_GREEN; // COLOR_LIGHT_VIOLET;
	public static final Color COLOR_FORCE_MATCH			= COLOR_LIGHT_GREEN;
	
}

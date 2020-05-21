package com.google.gwt.sample.stockwatcher.client;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.google.gwt.core.client.EntryPoint;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.i18n.client.DateTimeFormat;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.user.client.Random;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlexTable;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;

import javafx.scene.input.DataFormat;

/**
 * Entry point classes define <code>onModuleLoad()</code>.
 */
public class StockWatcher implements EntryPoint {
	private VerticalPanel mainPanel = new VerticalPanel();
	private FlexTable stocksFlexTable = new FlexTable();
	private HorizontalPanel addPanel = new HorizontalPanel();
	private TextBox newSymbolTextBox = new TextBox();
	private Button addStockButton = new Button("Add");
	private Label lastUpdatedLabel = new Label();
	private static final int REFRESH_INTERVAL = 5000;
	private List<String> stocks = new ArrayList<String>();
	
	private StockPriceServiceAsync stockPriceService = GWT.create(StockPriceService.class);
	private Label errorMsgLabel = new Label();
	
	public void onModuleLoad() {
		//Create table for stock data
		stocksFlexTable.setText(0, 0, "Symbol");
		stocksFlexTable.setText(0, 1, "Price");
		stocksFlexTable.setText(0, 2, "Change");
		stocksFlexTable.setText(0, 3, "Remove");
		
		stocksFlexTable.setCellPadding(6);

		// Add styles to elements in the stock list table.
		stocksFlexTable.getRowFormatter().addStyleName(0, "watchListHeader");
		stocksFlexTable.addStyleName("watchList");
		stocksFlexTable.getCellFormatter().addStyleName(0, 1, "watchListNumericColumn");
		stocksFlexTable.getCellFormatter().addStyleName(0, 2, "watchListNumericColumn");
		stocksFlexTable.getCellFormatter().addStyleName(0, 3, "watchListRemoveColumn");

		// Assemble Add Stock Panel
		addPanel.add(newSymbolTextBox);
		addPanel.add(addStockButton);
		addPanel.addStyleName("addPanel");
		
		errorMsgLabel.setStyleName("errorMessage");
		errorMsgLabel.setVisible(false);
		
		//Assemble Main Panel
		mainPanel.add(errorMsgLabel);
		mainPanel.add(stocksFlexTable);
		mainPanel.add(addPanel);
		mainPanel.add(lastUpdatedLabel);
		
		// Associate the Main panel with the HTML host page.
		RootPanel.get("stockList").add(mainPanel);
		
	    // Move cursor focus to the input box.
		newSymbolTextBox.setFocus(true);
		
		addStockButton.addClickHandler(new ClickHandler() {
			
			@Override
			public void onClick(ClickEvent event) {
				addStock();
			}
		});
		
		newSymbolTextBox.addKeyDownHandler( new KeyDownHandler() {
			
			@Override
			public void onKeyDown(KeyDownEvent event) {
				
				if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
					addStock();
				}
			}
		});
		
		newSymbolTextBox.setFocus(true);

	    Timer refreshTimer = new Timer() {
			@Override
			public void run() {
				refreshWatchList();
			}
	    };
	    refreshTimer.scheduleRepeating(REFRESH_INTERVAL);
		
	}
	
	private void addStock() {
		final String symbol = newSymbolTextBox.getText().toUpperCase().trim();
		newSymbolTextBox.setFocus(true);
		
		if (!symbol.matches("^[0-9A-Z\\.]{1,10}$")) {
	        Window.alert("'" + symbol + "' is not a valid symbol.");
	        newSymbolTextBox.selectAll();
		}
		
	    newSymbolTextBox.setText("");
	    
	    if (stocks.contains(symbol)) {
	    	return;
	    }
	    
	    int row = stocksFlexTable.getRowCount();
	    stocks.add(symbol);
	    stocksFlexTable.setText(row, 0, symbol);
	    stocksFlexTable.setWidget(row, 2, new Label());
	    stocksFlexTable.getCellFormatter().addStyleName(row, 1, "watchListNumericColumn");
	    stocksFlexTable.getCellFormatter().addStyleName(row, 2, "watchListNumericColumn");
	    stocksFlexTable.getCellFormatter().addStyleName(row, 3, "watchListRemoveColumn");
	    
	    Button removeStockButton = new Button("X");
	    removeStockButton.addStyleDependentName("remove");
	    removeStockButton.addClickHandler(new ClickHandler() {
			
			@Override
			public void onClick(ClickEvent event) {
				int removeIndex = stocks.indexOf(symbol);
				stocks.remove(removeIndex);
				stocksFlexTable.removeRow(removeIndex + 1); 
			}
		});
	    stocksFlexTable.setWidget(row, 3, removeStockButton);

	    refreshWatchList();
	    
	}

	protected void refreshWatchList() {
		
		if (stockPriceService == null ) {
			stockPriceService = GWT.create(StockPriceService.class);
		}
		
		AsyncCallback<StockPrice[]> callback = new AsyncCallback<StockPrice[]>() {
			
			@Override
			public void onSuccess(StockPrice[] result) {
				updateTable(result);
			}
			
			@Override
			public void onFailure(Throwable caught) {
				String details = caught.getMessage();
				
				if (caught instanceof DelistedException) {
					details = "Company '" + ((DelistedException) caught).getSymbol() + "' was delisted";
				}
				
				errorMsgLabel.setText("Error: " + details);
				errorMsgLabel.setVisible(true);
			}
		};
		
		stockPriceService.getPrices(stocks.toArray(new String[0]), callback);
		
	}

	private void updateTable(StockPrice[] prices) {
		for (int i = 0; i < prices.length; i++) {
			updateTable(prices[i]);
		}
		
	    // Display timestamp showing last refresh.
		DateTimeFormat dateFormat = DateTimeFormat.getFormat(DateTimeFormat.PredefinedFormat.DATE_TIME_MEDIUM);
		lastUpdatedLabel.setText("Last update: " + dateFormat.format(new Date()));
		
		// Clear any errors.
		errorMsgLabel.setVisible(false);
	}

	private void updateTable(StockPrice price) {
		if (!stocks.contains(price.getSymbol())) {
			return;
		}
		
		int row = stocks.indexOf(price.getSymbol()) + 1;
		
		String priceText = NumberFormat.getFormat("#,##0.00").format(price.getPrice());
		NumberFormat changeFormat = NumberFormat.getFormat("+#,##0.00;-#,##0.00");
		String changeText = changeFormat.format(price.getChange());
		String changePercentText = changeFormat.format(price.getChangePercent());
		
		stocksFlexTable.setText(row, 1, priceText);
		
		Label changeWidget= (Label) stocksFlexTable.getWidget(row, 2);
		changeWidget.setText(changeText + " (" + changePercentText + "%)");
		
		String changeStyleName = "noChange";
		if (price.getChangePercent() < -0.1f) {
		  changeStyleName = "negativeChange";
		}
		else if (price.getChangePercent() > 0.1f) {
		  changeStyleName = "positiveChange";
		}
		
		changeWidget.setStyleName(changeStyleName);
	}

}

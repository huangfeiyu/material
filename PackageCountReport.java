package com.etix.ticketing.reporting;

import com.etix.PriceCode;
import com.etix.PriceCodeService;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;

import javax.faces.bean.ViewScoped;

import com.etix.SalesChannel;
import com.etix.SalesChannelService;
import com.etix.SellableItem;
import com.etix.TransactionLog;
import com.etix.exception.EntityNotFoundException;
import com.etix.jsf.ShowMessage;
import com.etix.jsf.Template;
import com.etix.payment.CurrencyService;
import com.etix.payment.PriceComponentLabel;
import com.etix.payment.PriceComponentLabelService;
import com.etix.security.AccessPermission;
import com.etix.ticketing.FacilityActionList;
import com.etix.ticketing.Venue;
import com.etix.ticketing.Package;
import com.etix.ticketing.TicketPurchaseStatusLog;
import com.etix.ui.BaseDataProvider;
import com.etix.util.Database;
import com.etix.util.StringUtil;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;

@ViewScoped
public class PackageCountReport extends BaseDataProvider{
	private static final long serialVersionUID = 1L;
    
    private static final Logger logger = Logger.getLogger(PackageCountReport.class.getName());
    
    private static final int purchase_status_reserved = 1;
    
    private static final int purchase_status_purchased = 2;
    
    private static final int purchase_status_reserved_and_purchased = 3;
	
	private long venueId;
    private Venue venue;
	private Date transactionStartDateTime;
	private Date transactionEndDateTime;
    
    private Timestamp transactionStartTimestamp;
    private Timestamp transactionEndTimestamp;
    private List<Package> packages = null;
	private List<Long> selectedPriceComponents = new ArrayList<>();
	private List<Long> selectedChannels = new ArrayList<>();
	private List<Long> selectedRenewPriceCodes = new ArrayList<>();
	private List<Long> selectedNewPriceCodes = new ArrayList<>();
	private List<Long> selectedPackages = new ArrayList<>();
    private List<String> ticketStatuses = Arrays.asList(SellableItem.STATUS_RESERVED, SellableItem.STATUS_ISSUED + '/' + SellableItem.STATUS_REDEEMED);
    private List<String> selectedticketStatuses = new ArrayList<>(ticketStatuses);
    private final List<String> actionsForAdjust = Arrays.asList(TransactionLog.ACTION_ADJUST, TransactionLog.ACTION_VOID, TransactionLog.ACTION_WITHHOLD);
    private List<PriceComponentLabel> priceComponentLabels;
    private List<SalesChannel> salesChannels;
    private List<PriceCode> priceCodes;
    
    private int purchaseStatus;
    
    private final List<PackageCountReportRow> reportData = new ArrayList<>();
    
    private final Map<String, PackageCountReportRow> levels = new HashMap<>();
    
    private String commaDelimitedPackages;
    private String commaDelimitedChannels;
    private String commaDelimitedStatuses;
    private String commaDelimitedActionsForAdjust;
    private String commaDelimitedPriceComponents;
    private String commaDelimitedRenewPriceCodes;
    private String commaDelimitedNewPriceCodes;
    private String isoCode;
    
    private String commaDelimitedSelectedPriceCodes;
    
	private boolean displayReport = false;
	private TimeZone venueTimeZone;
    private boolean includeOrderFees = false;
    private boolean showExchangeFee = false;
	private String abbrTimeZone;

	@Template("/reporting/packageCountReport.xhtml")
	public void init() {
		if(request.getParameter("venue_id") == null) {
			venueId = ((Venue)session.getAttribute(FacilityActionList.CURRENT_VENUE)).getVenueID();
		} else {
			venueId = Long.valueOf(request.getParameter("venue_id"));
		}
		permissions.assertVenuePermission(venueId, AccessPermission.VIEW_BOX_OFFICE_SALES_REPORT);

		try {
			venue = Venue.findByPrimaryKey(venueId);
            packages = Arrays.asList(Package.getPackages("select p.package_id from package p where p.venue_id = ? order by p.event_begin_datetime desc", venueId));
            priceComponentLabels = getPriceComponents();
            salesChannels = SalesChannelService.getAllSalesChannels();
            priceCodes = PriceCodeService.getPriceCodes(venueId);
		} catch (SQLException | EntityNotFoundException e) {
			logger.log(Level.WARNING, e.getMessage(), e);
			return ;
		}
		venueTimeZone = venue.getTz();
		abbrTimeZone = venue.getTimeZone();
		
		transactionStartDateTime = getDefaultStartCalendar().getTime();
		transactionEndDateTime = getCurrentCalendar().getTime();
        
	}
	
	private Calendar getDefaultStartCalendar() {
		Calendar startDate = Calendar.getInstance(venueTimeZone);
		startDate.add(Calendar.MONTH, -1);
		startDate.set(Calendar.HOUR_OF_DAY, 0);
		startDate.set(Calendar.MINUTE, 0);
        startDate.set(Calendar.SECOND, 0);
        startDate.set(Calendar.MILLISECOND, 0);
		return startDate;
	}
	
	private Calendar getCurrentCalendar() {
		Calendar currentDate = Calendar.getInstance(venueTimeZone);
		currentDate.set(Calendar.HOUR_OF_DAY, 0);
		currentDate.set(Calendar.MINUTE, 0);
        currentDate.set(Calendar.SECOND, 0);
        currentDate.set(Calendar.MILLISECOND, 0);
        currentDate.add(Calendar.DAY_OF_MONTH, 1);
		return  currentDate;
	}
	
	private List<PriceComponentLabel> getPriceComponents() throws SQLException, EntityNotFoundException {
		String query = "select distinct pcl.price_component_label_id from price_component_label pcl, price_component_template pct where"
	                 + " pct.venue_id = " + venueId + " and pct.price_component_label_id = pcl.price_component_label_id";
		
		return PriceComponentLabelService.getPriceComponentLabels(query);
	}
    
    public List<PriceCode> getPriceCodes() throws EntityNotFoundException {
        return priceCodes;
    }
	
	public void generateReport() throws SQLException, EntityNotFoundException {
        levels.clear();
        reportData.clear();
        if(selectedRenewPriceCodes.isEmpty() && selectedNewPriceCodes.isEmpty()) {
            ShowMessage.error("please select at least one price code in \"Price Codes (Renewals)\" or \"Price Codes (New Orders)\"");
            return;
        }
        switch (selectedticketStatuses.size()) {
            case 1:
                switch (selectedticketStatuses.get(0)) {
                    case "RESERVED":
                        purchaseStatus = purchase_status_reserved;
                        break;
                    case "ISSUED/REDEEMED":
                        purchaseStatus = purchase_status_purchased;
                        break;
                    default:
                        throw new IllegalArgumentException("unexpected ticket status: " + selectedticketStatuses.get(0));
                }
                break;
            case 2:
                purchaseStatus = purchase_status_reserved_and_purchased;
                break;
            default:
                throw new IllegalArgumentException("unexpected ticket status: " + selectedticketStatuses);
        }
        transactionStartTimestamp = new Timestamp(transactionStartDateTime.getTime());
        transactionEndTimestamp = new Timestamp(transactionEndDateTime.getTime());
        commaDelimitedPackages = StringUtil.toCommaDelimitedString(selectedPackages);
        commaDelimitedChannels = StringUtil.toCommaDelimitedString(selectedChannels);
        commaDelimitedStatuses = StringUtil.toCommaDelimitedString(selectedticketStatuses.stream().map(e -> "'" + e + "'").collect(Collectors.toList()));
        commaDelimitedActionsForAdjust = StringUtil.toCommaDelimitedString(actionsForAdjust.stream().map(e -> "'" + e + "'").collect(Collectors.toList()));
        commaDelimitedPriceComponents = StringUtil.toCommaDelimitedString(selectedPriceComponents);
        commaDelimitedRenewPriceCodes = selectedRenewPriceCodes.isEmpty() ? null : StringUtil.toCommaDelimitedString(selectedRenewPriceCodes);
        commaDelimitedNewPriceCodes = selectedNewPriceCodes.isEmpty() ? null : StringUtil.toCommaDelimitedString(selectedNewPriceCodes);
        List<Long> selectedPriceCodes = new ArrayList<>(selectedRenewPriceCodes);
        selectedPriceCodes.addAll(selectedNewPriceCodes);
        commaDelimitedSelectedPriceCodes = StringUtil.toCommaDelimitedString(selectedPriceCodes);
        List<Long> selectedRenewPriceCodesCopy = new ArrayList<>(selectedRenewPriceCodes);
        selectedRenewPriceCodesCopy.retainAll(selectedNewPriceCodes);
        if(!selectedRenewPriceCodesCopy.isEmpty()) {
            ShowMessage.error("Same price code in both Renew and new.");
            return;
        }
        calculatePotentialPriceWithoutOrderFeesExchangeFee();
        calculateCollectedPrice();
        calculateOrdersOrderFeesExchangeFee();
        this.reportData.addAll(levels.values());
        Collections.sort(reportData, (o1, o2) -> o1.priceLevelName.compareTo(o2.priceLevelName));
        PackageCountReportRow total = new PackageCountReportRow();
        total.setPriceLevelName(msg.resolve("total"));
        reportData.forEach(row -> {
            total.setNewCollectedPrice(total.getNewCollectedPrice() + row.getNewCollectedPrice());
            total.setNewOrders(total.getNewOrders() + row.getNewOrders());
            total.setNewPackages(total.getNewPackages() + row.getNewPackages());
            total.setNewPotentialPrice(total.getNewPotentialPrice() + row.getNewPotentialPrice());
            total.setRenewCollectedPrice(total.getRenewCollectedPrice() + row.getRenewCollectedPrice());
            total.setRenewOrders(total.getRenewOrders() + row.getRenewOrders());
            total.setRenewPackages(total.getRenewPackages() + row.getRenewPackages());
            total.setRenewPotentialPrice(total.getRenewPotentialPrice() + row.getRenewPotentialPrice());
        });
        reportData.add(total);
        if(StringUtils.isBlank(isoCode)) {
            String sql = "select c.iso_code from currency c\n" +
                            "where exists \n" +
                            "(select 1 from package_price pp join package_price_code ppc on ppc.package_price_code_id = pp.package_price_code_id \n" +
                            "where ppc.package_id in ("+commaDelimitedPackages+") and c.currency_id = pp.currency_id\n" +
                            ")";
            List<String> isoCodes = Database.getList(sql);
            if(isoCodes.isEmpty()) {
                ShowMessage.error("No package price configured for the packages: " + commaDelimitedPackages);
                return;
            }
            if(isoCodes.size() > 1) {
                ShowMessage.warning("Too many isoCode were found for the packages: " + commaDelimitedPackages + isoCodes);
            }
            isoCode = isoCodes.get(0);
        }
        this.displayReport = true;
	}
    
    private void calculatePotentialPriceWithoutOrderFeesExchangeFee() throws SQLException, EntityNotFoundException {
        String sql = null;
        List<Map<String, String>> results = null;
        String ticketFilterCondition = getTicketStatusFilterCondition();
        switch (purchaseStatus) {
            case purchase_status_reserved ://only calculate for reserved packages
                sql = "select distinct t.price_level_name, t.currency_id\n" +
                        (commaDelimitedRenewPriceCodes == null ? "" :
                        ", sum(case when t.price_code_id in ("+commaDelimitedRenewPriceCodes+") then 1 else 0 end) over(partition by t.price_level_name) as number_of_renew_packages\n" +
                        ", sum(case when t.price_code_id in ("+commaDelimitedRenewPriceCodes+") then sum(tpd.amount) else 0 end) over(partition by t.price_level_name) as potential_amount_renew\n") +
                        (commaDelimitedNewPriceCodes == null ? "" :
                        ", sum(case when t.price_code_id in ("+commaDelimitedNewPriceCodes+") then 1 else 0 end) over(partition by t.price_level_name) as number_of_new_packages\n" +
                        ", sum(case when t.price_code_id in ("+commaDelimitedNewPriceCodes+") then sum(tpd.amount) else 0 end) over(partition by t.price_level_name) as potential_amount_new\n") +
                        "from ticket t \n" +
                        "join ticket_price_detail tpd  on t.ticket_id = tpd.ticket_id\n" +
                        "join etix_order eo on eo.order_id = t.order_id\n" +
                        "join (\n" +
                        "  select distinct tpsl.ticket_id, \n" +
                        "  last_value(tpsl.ticket_purchase_status_id) over(partition by tpsl.ticket_id order by tpsl.status_change_time range between unbounded preceding and unbounded following) as ticket_purchase_status_id\n" +
                        "  from ticket_purchase_status_log tpsl \n" +
                        "  join ticket t on t.ticket_id = tpsl.ticket_id \n" +
                        "  where tpsl.status_change_time between ? and ? and t.package_id in ("+commaDelimitedPackages+")\n" +
                        "  ) t1 on t1.ticket_id = t.ticket_id\n" +
                        "where t.package_id in ("+commaDelimitedPackages+")\n" +
                        "and eo.sales_channel_id in ("+commaDelimitedChannels+")\n" +
                        "and tpd.price_component_label_id in ("+commaDelimitedPriceComponents+")\n" +
                        "and t.price_code_id in ("+commaDelimitedSelectedPriceCodes+")\n" +
                        ticketFilterCondition +
                        "group by t.price_level_name, t.currency_id, t.price_code_id, t.package_ticket_id\n" +
                        "--having sum(case when t1.ticket_purchase_status_id <> 3 then 1 else 0 end) > 0\n" +
                        "order by t.price_level_name";
                results = Database.executeQuery(sql, transactionStartTimestamp, transactionEndTimestamp, transactionStartTimestamp, transactionEndTimestamp);
                break;
            case purchase_status_purchased ://only calculate for purchased packages
                sql = "select distinct t.price_level_name, t.currency_id\n" +
                        (commaDelimitedRenewPriceCodes == null ? "" :
                        ", sum(case when t.price_code_id in ("+commaDelimitedRenewPriceCodes+") then 1 else 0 end) over(partition by t.price_level_name) as number_of_renew_packages\n" +
                        ", sum(case when t.price_code_id in ("+commaDelimitedRenewPriceCodes+") then sum(case when t1.ticket_purchase_status_id in ("+TicketPurchaseStatusLog.PURCHASE_STATUS_VOID + ',' +TicketPurchaseStatusLog.PURCHASE_STATUS_UNVOID+")\n" +
                                "and exists(select 1 from ticket_purchase_status_log where ticket_purchase_status_id = "+TicketPurchaseStatusLog.PURCHASE_STATUS_PURCHASED+" and ticket_id = t.ticket_id) then tpd.amount when t1.ticket_purchase_status_id = "+TicketPurchaseStatusLog.PURCHASE_STATUS_PURCHASED+" then tpd.amount else 0 end) else 0 end) over(partition by t.price_level_name) as potential_amount_renew\n") +
                        (commaDelimitedNewPriceCodes == null ? "" :
                        ", sum(case when t.price_code_id in ("+commaDelimitedNewPriceCodes+") then 1 else 0 end) over(partition by t.price_level_name) as number_of_new_packages\n" +
                        ", sum(case when t.price_code_id in ("+commaDelimitedNewPriceCodes+") then sum(case when t1.ticket_purchase_status_id in ("+TicketPurchaseStatusLog.PURCHASE_STATUS_VOID + ',' +TicketPurchaseStatusLog.PURCHASE_STATUS_UNVOID+")\n" +
                                "and exists(select 1 from ticket_purchase_status_log where ticket_purchase_status_id = "+TicketPurchaseStatusLog.PURCHASE_STATUS_PURCHASED+" and ticket_id = t.ticket_id) then tpd.amount when t1.ticket_purchase_status_id = "+TicketPurchaseStatusLog.PURCHASE_STATUS_PURCHASED+" then tpd.amount else 0 end) else 0 end) over(partition by t.price_level_name) as potential_amount_new\n") +
                        "from ticket t \n" +
                        "join ticket_price_detail tpd  on t.ticket_id = tpd.ticket_id\n" +
                        "join etix_order eo on eo.order_id = t.order_id\n" +
                        "join (\n" +
                        "  select distinct tpsl.ticket_id, \n" +
                        "  last_value(tpsl.ticket_purchase_status_id) over(partition by tpsl.ticket_id order by tpsl.status_change_time range between unbounded preceding and unbounded following) as ticket_purchase_status_id\n" +
                        "  from ticket_purchase_status_log tpsl \n" +
                        "  join ticket t on t.ticket_id = tpsl.ticket_id \n" +
                        "  where tpsl.status_change_time between ? and ? and t.package_id in ("+commaDelimitedPackages+")\n" +
                        "  ) t1 on t1.ticket_id = t.ticket_id\n" +
                        "where t.package_id in ("+commaDelimitedPackages+")\n" +
                        "and eo.sales_channel_id in ("+commaDelimitedChannels+")\n" +
                        "and tpd.price_component_label_id in ("+commaDelimitedPriceComponents+")\n" +
                        "and t.price_code_id in ("+commaDelimitedSelectedPriceCodes+")\n" +
                        ticketFilterCondition +
                        "group by t.price_level_name, t.currency_id, t.price_code_id, t.package_ticket_id\n" +
                        "having sum(case when t1.ticket_purchase_status_id <> 3 then 1 else 0 end) > 0\n" +
                        "order by t.price_level_name";
                results = Database.executeQuery(sql, transactionStartTimestamp, transactionEndTimestamp);
                break;
            case purchase_status_reserved_and_purchased ://calculate for both reserved and purchased packages
                sql = "select distinct t.price_level_name, t.currency_id\n" +
                        (commaDelimitedRenewPriceCodes == null ? "" :
                        ", sum(case when t.price_code_id in ("+commaDelimitedRenewPriceCodes+") then 1 else 0 end) over(partition by t.price_level_name) as number_of_renew_packages\n" +
                        ", sum(case when t.price_code_id in ("+commaDelimitedRenewPriceCodes+") then sum(case when t1.ticket_purchase_status_id in ("+TicketPurchaseStatusLog.PURCHASE_STATUS_VOID + ',' +TicketPurchaseStatusLog.PURCHASE_STATUS_UNVOID+")\n" +
                                "and exists(select 1 from ticket_purchase_status_log where ticket_purchase_status_id = "+TicketPurchaseStatusLog.PURCHASE_STATUS_PURCHASED+" and ticket_id = t.ticket_id) then tpd.amount when t1.ticket_purchase_status_id in ("+TicketPurchaseStatusLog.PURCHASE_STATUS_PURCHASED+ ',' + TicketPurchaseStatusLog.PURCHASE_STATUS_RESERVED +") then tpd.amount else 0 end) else 0 end) over(partition by t.price_level_name) as potential_amount_renew\n") +
                        (commaDelimitedNewPriceCodes == null ? "" :
                        ", sum(case when t.price_code_id in ("+commaDelimitedNewPriceCodes+") then 1 else 0 end) over(partition by t.price_level_name) as number_of_new_packages\n" +
                        ", sum(case when t.price_code_id in ("+commaDelimitedNewPriceCodes+") then sum(case when t1.ticket_purchase_status_id in ("+TicketPurchaseStatusLog.PURCHASE_STATUS_VOID + ',' +TicketPurchaseStatusLog.PURCHASE_STATUS_UNVOID+")\n" +
                                "and exists(select 1 from ticket_purchase_status_log where ticket_purchase_status_id = "+TicketPurchaseStatusLog.PURCHASE_STATUS_PURCHASED+" and ticket_id = t.ticket_id) then tpd.amount when t1.ticket_purchase_status_id in ("+TicketPurchaseStatusLog.PURCHASE_STATUS_PURCHASED+ ',' + TicketPurchaseStatusLog.PURCHASE_STATUS_RESERVED +") then tpd.amount else 0 end) else 0 end) over(partition by t.price_level_name) as potential_amount_new\n") +
                        "from ticket t \n" +
                        "join ticket_price_detail tpd  on t.ticket_id = tpd.ticket_id\n" +
                        "join etix_order eo on eo.order_id = t.order_id\n" +
                        "join (\n" +
                        "  select distinct tpsl.ticket_id, \n" +
                        "  last_value(tpsl.ticket_purchase_status_id) over(partition by tpsl.ticket_id order by tpsl.status_change_time range between unbounded preceding and unbounded following) as ticket_purchase_status_id\n" +
                        "  from ticket_purchase_status_log tpsl \n" +
                        "  join ticket t on t.ticket_id = tpsl.ticket_id \n" +
                        "  where tpsl.status_change_time between ? and ? and t.package_id in ("+commaDelimitedPackages+")\n" +
                        "  ) t1 on t1.ticket_id = t.ticket_id\n" +
                        "where t.package_id in ("+commaDelimitedPackages+")\n" +
                        "and eo.sales_channel_id in ("+commaDelimitedChannels+")\n" +
                        "and tpd.price_component_label_id in ("+commaDelimitedPriceComponents+")\n" +
                        "and t.price_code_id in ("+commaDelimitedSelectedPriceCodes+")\n" +
                        ticketFilterCondition +
                        "group by t.price_level_name, t.currency_id, t.price_code_id, t.package_ticket_id\n" +
                        "having sum(case when t1.ticket_purchase_status_id <> 3 then 1 else 0 end) > 0\n" +
                        "order by t.price_level_name";
                results = Database.executeQuery(sql, transactionStartTimestamp, transactionEndTimestamp, transactionStartTimestamp, transactionEndTimestamp);
                break;
            default:
                 throw new RuntimeException("Wrong purchase status " + purchaseStatus);
            
        }
        if(!results.isEmpty()) {
            isoCode = CurrencyService.findByPrimaryKey(results.get(0).get("CURRENCY_ID")).getIsoCode();
        }
        results.stream().map(map -> {
            PackageCountReportRow row = new PackageCountReportRow();
            row.setPriceLevelName(map.get("PRICE_LEVEL_NAME"));
            String numberOfPackages = map.get("NUMBER_OF_RENEW_PACKAGES");
            if(numberOfPackages != null) {
                row.setRenewPackages(Integer.parseInt(numberOfPackages));
            }
            numberOfPackages = map.get("NUMBER_OF_NEW_PACKAGES");
            if(numberOfPackages != null) {
                row.setNewPackages(Integer.parseInt(numberOfPackages));
            }
            String potentialAmount = map.get("POTENTIAL_AMOUNT_RENEW");
            if(potentialAmount != null) {
                row.setRenewPotentialPrice(Double.parseDouble(potentialAmount));
            }
            potentialAmount = map.get("POTENTIAL_AMOUNT_NEW");
            if(potentialAmount != null) {
                row.setNewPotentialPrice(Double.parseDouble(potentialAmount));
            }
            return row;
        }).collect(Collectors.toList()).forEach(row -> levels.put(row.getPriceLevelName(), row));
    }
    
    private void calculateCollectedPrice() {
        String ticketFilterCondition = null;
        switch (purchaseStatus) {
            case purchase_status_reserved :
                ticketFilterCondition = "and t1.ticket_purchase_status_id = "+TicketPurchaseStatusLog.PURCHASE_STATUS_RESERVED+"\n";
                break;
            case purchase_status_purchased :
                ticketFilterCondition = "and t1.ticket_purchase_status_id <> "+TicketPurchaseStatusLog.PURCHASE_STATUS_RESERVED+"\n";
                break;
            case purchase_status_reserved_and_purchased :
                ticketFilterCondition = "";
                break;
            default :
                throw new RuntimeException("Wrong purchase status " + purchaseStatus);
        }
        String sql = "select distinct t.price_level_name\n" +
            (commaDelimitedRenewPriceCodes == null ? "" :
            ", sum(case when t.price_code_id in ("+commaDelimitedRenewPriceCodes+") then sum(tcd.amount) else 0 end) over(partition BY t.price_level_name) as collected_price_renew\n") +
            (commaDelimitedNewPriceCodes == null ? "" :
            ", sum(case when t.price_code_id in ("+commaDelimitedNewPriceCodes+") then sum(tcd.amount) else 0 end) over(partition BY t.price_level_name) as collected_price_new\n") +
            "from\n" +
            "ticket t\n" +
            "join transaction_component_detail tcd on t.ticket_id = tcd.ticket_id\n" +
            "join etix_order eo on eo.order_id = t.order_id\n" +
            "join (\n" +
            "  select distinct tpsl.ticket_id, \n" +
            "  last_value(tpsl.ticket_purchase_status_id) over(partition by tpsl.ticket_id order by tpsl.status_change_time range between unbounded preceding and unbounded following) as ticket_purchase_status_id\n" +
            "  from ticket_purchase_status_log tpsl \n" +
            "  join ticket t on t.ticket_id = tpsl.ticket_id \n" +
            "  where tpsl.status_change_time between ? and ? and t.package_id in ("+commaDelimitedPackages+")\n" +
            "  ) t1 on t1.ticket_id = t.ticket_id\n" +
            "where t.package_id in ("+commaDelimitedPackages+")\n" +
            "and t.price_code_id in ("+commaDelimitedSelectedPriceCodes+")\n" +
            "and eo.sales_channel_id in ("+commaDelimitedChannels+")\n" +
            ticketFilterCondition +
            "and tcd.price_component_label_id in ("+commaDelimitedPriceComponents+")\n" +
            "and exists(select 1 from transaction x where x.order_id = eo.order_id and x.price <> 0 and tcd.transaction_id = x.transaction_id and x.action in ('sell','payment','reserve','refund','pull') and x.transaction_date between ? and ?)\n" +
            "group by t.price_level_name, t.price_code_id, t.package_ticket_id\n" +
            "having sum(case when t1.ticket_purchase_status_id <> 3 then 1 else 0 end) > 0";
        List<Map<String, String>> results= Database.executeQuery(sql, transactionStartTimestamp, transactionEndTimestamp, transactionStartTimestamp, transactionEndTimestamp);
        results.forEach(map -> {
            String level = map.get("PRICE_LEVEL_NAME");
            PackageCountReportRow row = levels.get(level);
            if(row == null) {
                row = new PackageCountReportRow();
                row.setPriceLevelName(level);
                levels.put(level, row);
            }
            String collectedPriceRenew = map.get("COLLECTED_PRICE_RENEW");
            if(collectedPriceRenew != null) {
                row.setRenewCollectedPrice(Double.parseDouble(collectedPriceRenew));
            }
            String collectedPriceNew = map.get("COLLECTED_PRICE_NEW");
            if(collectedPriceNew != null) {
                row.setNewCollectedPrice(Double.parseDouble(collectedPriceNew));
            }
        });
    }

    private String getTicketStatusFilterCondition() throws RuntimeException {
        String ticketFilterCondition = null;
        switch (purchaseStatus) {
            case purchase_status_reserved :
                ticketFilterCondition = "and t1.ticket_purchase_status_id = "+TicketPurchaseStatusLog.PURCHASE_STATUS_RESERVED+"\n" +
                    "and exists(select 1 from transaction x where x.order_id = eo.order_id and x.action in ('sell','payment','reserve','refund','pull') and x.price <> 0 and x.transaction_date between ? and ?)\n";
                break;
            case purchase_status_purchased :
                ticketFilterCondition = "and t1.ticket_purchase_status_id <> "+TicketPurchaseStatusLog.PURCHASE_STATUS_RESERVED+"\n";
                break;
            case purchase_status_reserved_and_purchased :
                ticketFilterCondition = "and (t1.ticket_purchase_status_id = "+TicketPurchaseStatusLog.PURCHASE_STATUS_RESERVED+ "\n" +
                    "and exists(select 1 from transaction x where x.order_id = eo.order_id and x.action in ('sell','payment','reserve','refund','pull') and x.price <> 0 and x.transaction_date between ? and ?)\n" +
                    "or t1.ticket_purchase_status_id <> "+TicketPurchaseStatusLog.PURCHASE_STATUS_RESERVED+")\n";
                break;
            default :
                throw new RuntimeException("Wrong purchase status " + purchaseStatus);
        }
        return ticketFilterCondition;
    }
    
    private void calculateOrdersOrderFeesExchangeFee() {
        String ticketFilterCondition = getTicketStatusFilterCondition();
        String sql = "SELECT DISTINCT t2.order_price_level_name,\n" +
                    "  SUM(case t2.new_or_renew when 'renew' then 1 else 0 end) over (partition BY t2.order_price_level_name)    AS number_of_order_renew,\n" +
                    "  SUM(case t2.new_or_renew when 'new' then 1 else 0 end) over (partition BY t2.order_price_level_name)    AS number_of_order_new,\n" +
                    "  SUM(case t2.new_or_renew when 'renew' then MIN(t2.order_fee) else 0 end) over (partition BY t2.order_price_level_name)    AS order_fee_renew,\n" +
                    "  SUM(case t2.new_or_renew when 'renew' then MIN(t2.collected_order_fee) else 0 end) over (partition BY t2.order_price_level_name)    AS collected_order_fee_renew,\n" +
                    "  SUM(case t2.new_or_renew when 'new' then MIN(t2.order_fee) else 0 end) over (partition BY t2.order_price_level_name)    AS order_fee_new,\n" +
                    "  SUM(case t2.new_or_renew when 'new' then MIN(t2.collected_order_fee) else 0 end) over (partition BY t2.order_price_level_name)    AS collected_order_fee_new,\n" +
                    "  SUM(case t2.new_or_renew when 'renew' then MIN(t2.handling_fee) else 0 end) over (partition BY t2.order_price_level_name) AS handling_fee_renew,\n" +
                    "  SUM(case t2.new_or_renew when 'renew' then MIN(t2.collected_handling_fee) else 0 end) over (partition BY t2.order_price_level_name) AS collected_handling_fee_renew,\n" +
                    "  SUM(case t2.new_or_renew when 'new' then MIN(t2.handling_fee) else 0 end) over (partition BY t2.order_price_level_name) AS handling_fee_new,\n" +
                    "  SUM(case t2.new_or_renew when 'new' then MIN(t2.collected_handling_fee) else 0 end) over (partition BY t2.order_price_level_name) AS collected_handling_fee_new,\n" +
                    "  -- 6 : TransactionType.EXCHANGE_FEE_CHARGE, 7 : TransactionType.EXCHANGE_FEE_REFUND\n" +
                    "  SUM(case t2.new_or_renew when 'renew' then SUM(\n" +
                    "  CASE at.type_id\n" +
                    "    WHEN 6\n" +
                    "    THEN at.amount\n" +
                    "    WHEN 7\n" +
                    "    THEN -1*at.amount\n" +
                    "    ELSE 0\n" +
                    "  END) else 0 end) over (partition BY t2.order_price_level_name) exchange_fee_renew,\n" +
                    "  SUM(case t2.new_or_renew when 'new' then SUM(\n" +
                    "  CASE at.type_id\n" +
                    "    WHEN 6\n" +
                    "    THEN at.amount\n" +
                    "    WHEN 7\n" +
                    "    THEN -1*at.amount\n" +
                    "    ELSE 0\n" +
                    "  END) else 0 end) over (partition BY t2.order_price_level_name) exchange_fee_new\n" +
                    "FROM\n" +
                    "  (SELECT t1.order_id,\n" +
                    "    t1.order_price_level_name,\n" +
                    "    t1.order_fee    + SUM(case when x.action in ("+commaDelimitedActionsForAdjust+") then x.order_fee else 0 end)    AS order_fee,\n" +
                    "    t1.handling_fee + SUM(case when x.action in ("+commaDelimitedActionsForAdjust+") then x.handling_fee else 0 end) AS handling_fee,\n" +
                    "    SUM(case when x.action in ('sell','payment','reserve','refund','pull') then x.order_fee else 0 end)    AS collected_order_fee,\n" +
                    "    SUM(case when x.action in ('sell','payment','reserve','refund','pull') then x.handling_fee else 0 end) AS collected_handling_fee,\n" +
                    "    t1.new_or_renew\n" +
                    "  FROM\n" +
                    "    ( SELECT DISTINCT eo.order_id,\n" +
                    "      eo.order_fee,\n" +
                    "      eo.handling_fee,\n" +
                    "      first_value(t.price_level_name) over(partition BY eo.order_id order by t.price DESC rows BETWEEN unbounded preceding AND unbounded following) AS order_price_level_name,\n" +
                    "      min(case when min(t.price_code_id) in ("+commaDelimitedRenewPriceCodes+") then 'renew' else 'new' end) over(partition BY eo.order_id) as new_or_renew\n" +
                    "    FROM etix_order eo\n" +
                    "    JOIN ticket t\n" +
                    "    ON t.order_id = eo.order_id\n" +
                    "    join (\n" +
                    "       select distinct tpsl.ticket_id, \n" +
                    "           last_value(tpsl.ticket_purchase_status_id) over(partition by tpsl.ticket_id order by tpsl.status_change_time range between unbounded preceding and unbounded following) as ticket_purchase_status_id\n" +
                    "       from ticket_purchase_status_log tpsl \n" +
                    "       join ticket t on t.ticket_id = tpsl.ticket_id \n" +
                    "       where tpsl.status_change_time between ? and ? and t.package_id in ("+commaDelimitedPackages+")\n" +
                    "    ) t1 on t1.ticket_id = t.ticket_id\n" +
                    "    WHERE EXISTS\n" +
                    "      (SELECT 1\n" +
                    "      FROM ticket_price_detail tpd\n" +
                    "      WHERE tpd.ticket_id               = t.ticket_id\n" +
                    "      AND tpd.price_component_label_id IN ("+commaDelimitedPriceComponents+")\n" +
                    "      )\n" +
                    "    AND t.package_id        IN ("+commaDelimitedPackages+")\n" +
                    "    AND eo.sales_channel_id IN ("+commaDelimitedChannels+")\n" +
                    ticketFilterCondition +
                    "    AND t.price_code_id     IN ("+commaDelimitedSelectedPriceCodes+")\n" +
                    "    and t.package_ticket_id in (select t.package_ticket_id from (\n" +
                    "       select distinct t.package_ticket_id, tpsl.ticket_id, \n" +
                    "           last_value(tpsl.ticket_purchase_status_id) over(partition by tpsl.ticket_id order by tpsl.status_change_time range between unbounded preceding and unbounded following) as ticket_purchase_status_id\n" +
                    "       from ticket_purchase_status_log tpsl \n" +
                    "       join ticket t on t.ticket_id = tpsl.ticket_id \n" +
                    "       where tpsl.status_change_time between ? and ? and t.package_id in ("+commaDelimitedPackages+")\n" +
                    "       ) t group by t.package_ticket_id having sum(case when t.ticket_purchase_status_id <> 3 then 1 else 0 end) > 0)\n" +
                    "    GROUP BY eo.order_id,\n" +
                    "      eo.order_fee,\n" +
                    "      eo.handling_fee,\n" +
                    "      t.price_level_name,\n" +
                    "      t.price\n" +
                    "    ) t1\n" +
                    "  JOIN transaction x\n" +
                    "  ON t1.order_id = x.order_id\n" +
                    "  GROUP BY t1.order_price_level_name,\n" +
                    "    t1.order_id,\n" +
                    "    t1.order_fee,\n" +
                    "    t1.handling_fee,\n" +
                    "    t1.new_or_renew\n" +
                    "  ) t2\n" +
                    "LEFT JOIN order_transaction ot\n" +
                    "ON ot.order_id = t2.order_id\n" +
                    "LEFT JOIN account_transaction at\n" +
                    "ON at.id = ot.account_transaction_id\n" +
                    "GROUP BY t2.order_price_level_name,\n" +
                    "  t2.order_id, t2.new_or_renew\n" +
                    "ORDER BY t2.order_price_level_name";
        List<Map<String, String>> results = null;
        if(purchase_status_purchased == purchaseStatus) {
            results= Database.executeQuery(sql, transactionStartTimestamp, transactionEndTimestamp, transactionStartTimestamp, transactionEndTimestamp);
        } else {
            results= Database.executeQuery(sql, transactionStartTimestamp, transactionEndTimestamp, transactionStartTimestamp, transactionEndTimestamp, transactionStartTimestamp, transactionEndTimestamp);
        }
        results.forEach(map -> {
            String level = map.get("ORDER_PRICE_LEVEL_NAME");
            PackageCountReportRow row = levels.get(level);
            if(row == null) return;
            String numberOfRenewOrder = map.get("NUMBER_OF_ORDER_RENEW");
            row.setRenewOrders(numberOfRenewOrder == null ? null : Integer.parseInt(numberOfRenewOrder));
            String numberOfNewOrder = map.get("NUMBER_OF_ORDER_NEW");
            row.setNewOrders(numberOfNewOrder == null ? null : Integer.parseInt(numberOfNewOrder));
            double feesRenew = 0;
            double feesNew = 0;
            double collectedFeesRenew = 0;
            double collectedfeesNew = 0;
            if(includeOrderFees) {
                feesRenew += Double.parseDouble(map.get("ORDER_FEE_RENEW"));
                feesRenew += Double.parseDouble(map.get("HANDLING_FEE_RENEW"));
                collectedFeesRenew += Double.parseDouble(map.get("COLLECTED_ORDER_FEE_RENEW"));
                collectedFeesRenew += Double.parseDouble(map.get("COLLECTED_HANDLING_FEE_RENEW"));
                feesNew += Double.parseDouble(map.get("ORDER_FEE_NEW"));
                feesNew += Double.parseDouble(map.get("HANDLING_FEE_NEW"));
                collectedfeesNew += Double.parseDouble(map.get("COLLECTED_ORDER_FEE_NEW"));
                collectedfeesNew += Double.parseDouble(map.get("COLLECTED_HANDLING_FEE_NEW"));
            }
            if(showExchangeFee) {
                double exchangeFeeRenew = Double.parseDouble(map.get("EXCHANGE_FEE_RENEW"));
                double exchangeFeeNew = Double.parseDouble(map.get("EXCHANGE_FEE_NEW"));
                feesRenew += exchangeFeeRenew;
                feesNew += exchangeFeeNew;
                collectedFeesRenew += exchangeFeeRenew;
                collectedfeesNew += exchangeFeeNew;
            }
            row.setRenewPotentialPrice(row.getRenewPotentialPrice() + feesRenew);
            row.setRenewCollectedPrice(row.getRenewCollectedPrice() + collectedFeesRenew);
            row.setNewPotentialPrice(row.getNewPotentialPrice() + feesNew);
            row.setNewCollectedPrice(row.getNewCollectedPrice() + collectedfeesNew);
        });
        
    }
	
	public String formatNumber(Object obj) {
		return String.format("%.2f", obj);
	}
	
	public String formatCurrencyCode(String currencyCode) {
		if("USD".equals(currencyCode)) {
			return "$";
		}
		return currencyCode;
	}

	public long getVenueId() {
		return venueId;
	}

	public void setVenueId(long venueId) {
		this.venueId = venueId;
	}
	
	public String getVenueName() {
		try {
			return Venue.findByPrimaryKey(venueId).getName();
		} catch (SQLException | EntityNotFoundException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
			return "";
		} 
	}

    public Date getTransactionStartDateTime() {
        return transactionStartDateTime;
    }

    public void setTransactionStartDateTime(Date transactionStartDateTime) {
        this.transactionStartDateTime = transactionStartDateTime;
    }

    public Date getTransactionEndDateTime() {
        return transactionEndDateTime;
    }

    public void setTransactionEndDateTime(Date transactionEndDateTime) {
        this.transactionEndDateTime = transactionEndDateTime;
    }

    public List<Package> getPackages() {
        return packages;
    }

    public void setPackages(List<Package> packages) {
        this.packages = packages;
    }

    public List<Long> getSelectedPackages() {
        return selectedPackages;
    }

    public void setSelectedPackages(List<Long> selectedPackages) {
        this.selectedPackages = selectedPackages;
    }

    public List<String> getTicketStatuses() {
        return ticketStatuses;
    }

    public void setTicketStatuses(List<String> ticketStatuses) {
        this.ticketStatuses = ticketStatuses;
    }

    public TimeZone getVenueTimeZone() {
        return venueTimeZone;
    }

    public boolean isIncludeOrderFees() {
        return includeOrderFees;
    }

    public void setIncludeOrderFees(boolean includeOrderFees) {
        this.includeOrderFees = includeOrderFees;
    }

    public boolean isShowExchangeFee() {
        return showExchangeFee;
    }

    public void setShowExchangeFee(boolean showExchangeFee) {
        this.showExchangeFee = showExchangeFee;
    }

    public List<PriceComponentLabel> getPriceComponentLabels() {
        return priceComponentLabels;
    }

    public List<SalesChannel> getSalesChannels() {
        return salesChannels;
    }

	public List<Long> getSelectedPriceComponents() {
		if(selectedPriceComponents.isEmpty()) {
            selectedPriceComponents.addAll(priceComponentLabels.stream().map(PriceComponentLabel::getPriceComponentLabelId).collect(Collectors.toList()));
		}
		return selectedPriceComponents;
	}

	public void setSelectedPriceComponents(List<Long> selectedPriceComponents) {
		this.selectedPriceComponents = selectedPriceComponents;
	}
	
	public String getSelectedPriceComponentsName() throws EntityNotFoundException, SQLException {
		List<String> priceComponentNameList = new ArrayList<>();
		for(Long id : selectedPriceComponents) {
			priceComponentNameList.add(msg.get(PriceComponentLabelService.findByPriceComponentLabel(id).getBundleKey()).toString());
		}
		return StringUtil.toCommaDelimitedString(priceComponentNameList);
	}

    public List<Long> getSelectedRenewPriceCodes() {
        return selectedRenewPriceCodes;
    }

    public void setSelectedRenewPriceCodes(List<Long> selectedRenewPriceCodes) {
        this.selectedRenewPriceCodes = selectedRenewPriceCodes;
    }

    public List<Long> getSelectedNewPriceCodes() {
        return selectedNewPriceCodes;
    }

    public void setSelectedNewPriceCodes(List<Long> selectedNewPriceCodes) {
        this.selectedNewPriceCodes = selectedNewPriceCodes;
    }

    public List<String> getSelectedticketStatuses() {
        return selectedticketStatuses;
    }

    public void setSelectedticketStatuses(List<String> selectedticketStatuses) {
        this.selectedticketStatuses = selectedticketStatuses;
    }
    
	public List<Long> getSelectedChannels() {
		if(selectedChannels.isEmpty()) {
			selectedChannels.addAll(salesChannels.stream().map(SalesChannel::getChannelId).collect(Collectors.toList()));
		}
		return selectedChannels;
	}

	public void setSelectedChannels(List<Long> selectedChannels) {
		this.selectedChannels = selectedChannels;
	}
	
	public String getSelectedChannelsName() {
		List<String> channelsName = selectedChannels.stream().map(id -> msg.get(SalesChannelService.findByPrimaryKey(id).getName()).toString()).collect(Collectors.toList());
		return StringUtil.toCommaDelimitedString(channelsName);
	}

	public List<PackageCountReportRow> getReportData() {
		return reportData;
	}
    
    private String getSelectedPackageNames() {
        StringBuilder sb = new StringBuilder();
        selectedPackages.forEach(packageId -> {
            try {
                String packageName = Package.findByPrimaryKey(packageId).getName();
                sb.append(packageName).append(',');
            } catch (SQLException | EntityNotFoundException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        });
        sb.deleteCharAt(sb.lastIndexOf(","));
        return sb.toString();
    }

	public boolean isDisplayReport() {
		return displayReport;
	}
    
    private String getSelectedPriceCodeNames(List<Long> selectedPriceCodeIds) {
        return StringUtil.toCommaDelimitedString(priceCodes.stream().filter(e -> selectedPriceCodeIds.contains(e.getPriceCodeID())).map(PriceCode::getName).collect(Collectors.toList()));
    }

	public List<String> getReportHeader() throws EntityNotFoundException, SQLException {
        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT);
        dateFormat.setTimeZone(venueTimeZone);
		ArrayList<String> headerInfo = new ArrayList<>();
		headerInfo.add(msg.resolve("packageCountReport"));
		headerInfo.add(msg.resolve("organizationName") + ':' + venue.getOrganization().getName());
		headerInfo.add(msg.resolve("venue") + ':' + getVenueName());
		headerInfo.add(msg.resolve("etixReport.reportedGenerated") + ':' + dateFormat.format(Calendar.getInstance().getTime()));
		headerInfo.add(msg.resolve("packages") + ':' + getSelectedPackageNames());
		headerInfo.add(msg.resolve("transactionDateRange") + ':' + dateFormat.format(transactionStartDateTime) + " - " + dateFormat.format(transactionEndDateTime));
		headerInfo.add(msg.resolve("reporting.priceComponent") + ':' + getSelectedPriceComponentsName());
		headerInfo.add(msg.resolve("salesChannel") + ':' + getSelectedChannelsName());
		headerInfo.add(msg.resolve("ticket.status") + ':' + commaDelimitedStatuses);
		headerInfo.add(msg.resolve("report.renewingPriceCodes") + ':' + getSelectedPriceCodeNames(selectedRenewPriceCodes));
		headerInfo.add(msg.resolve("report.newingPriceCodes") + ':' + getSelectedPriceCodeNames(selectedNewPriceCodes));
        if(includeOrderFees) {
            headerInfo.add(msg.resolve("reporting.includeOrderHandlingFees"));
        } else {
            headerInfo.add("Does Not " + msg.resolve("reporting.includeOrderHandlingFees"));
        }
        if(showExchangeFee) {
            headerInfo.add(msg.resolve("reporting.showExchangeFee"));
        } else {
            headerInfo.add("Does Not " + msg.resolve("reporting.showExchangeFee"));
        }
		return headerInfo;
	}

	public TimeZone getTimeZone() {
		return venueTimeZone;
	}

	public String getAbbrTimeZone() {
		return abbrTimeZone;
	}

    public String getIsoCode() {
        return isoCode;
    }
    
    public static class PackageCountReportRow {
        
        private String priceLevelName;
        
        private int renewOrders;
        
        private int renewPackages;
        
        private double renewPotentialPrice;
        
        private double renewCollectedPrice;
        
        private int newOrders;
        
        private int newPackages;
        
        private double newPotentialPrice;
        
        private double newCollectedPrice;

        public String getPriceLevelName() {
            return priceLevelName;
        }

        public void setPriceLevelName(String priceLevelName) {
            this.priceLevelName = priceLevelName;
        }

        public int getRenewOrders() {
            return renewOrders;
        }

        public void setRenewOrders(int renewOrders) {
            this.renewOrders = renewOrders;
        }

        public int getRenewPackages() {
            return renewPackages;
        }

        public void setRenewPackages(int renewPackages) {
            this.renewPackages = renewPackages;
        }

        public double getRenewPotentialPrice() {
            return renewPotentialPrice;
        }

        public void setRenewPotentialPrice(double renewPotentialPrice) {
            this.renewPotentialPrice = renewPotentialPrice;
        }

        public double getRenewCollectedPrice() {
            return renewCollectedPrice;
        }

        public void setRenewCollectedPrice(double renewCollectedPrice) {
            this.renewCollectedPrice = renewCollectedPrice;
        }

        public int getNewOrders() {
            return newOrders;
        }

        public void setNewOrders(int newOrders) {
            this.newOrders = newOrders;
        }

        public int getNewPackages() {
            return newPackages;
        }

        public void setNewPackages(int newPackages) {
            this.newPackages = newPackages;
        }

        public double getNewPotentialPrice() {
            return newPotentialPrice;
        }

        public void setNewPotentialPrice(double newPotentialPrice) {
            this.newPotentialPrice = newPotentialPrice;
        }

        public double getNewCollectedPrice() {
            return newCollectedPrice;
        }

        public void setNewCollectedPrice(double newCollectedPrice) {
            this.newCollectedPrice = newCollectedPrice;
        }

        @Override
        public String toString() {
            return "PackageCountReportRow{" + "priceLevelName=" + priceLevelName + ", renewOrders=" + renewOrders + ", renewPackages=" + renewPackages + ", renewPotentialPrice=" + renewPotentialPrice + ", renewCollectedPrice=" + renewCollectedPrice + ", newOrders=" + newOrders + ", newPackages=" + newPackages + ", newPotentialPrice=" + newPotentialPrice + ", newCollectedPrice=" + newCollectedPrice + '}';
        }
        
    }
    
}

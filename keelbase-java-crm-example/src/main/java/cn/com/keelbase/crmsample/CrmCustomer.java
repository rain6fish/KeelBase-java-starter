package cn.com.keelbase.crmsample;

/** 存量 CRM 客户。 */
public record CrmCustomer(long id, String name, String company, String email, String status) {
}

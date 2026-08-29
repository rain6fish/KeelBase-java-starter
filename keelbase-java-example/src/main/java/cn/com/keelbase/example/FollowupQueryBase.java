package cn.com.keelbase.example;

/** 跟进查询的父类字段：分页参数（演示继承字段会被导出为工具参数）。 */
class FollowupQueryBase {
    private Integer page;
    private Integer limit;

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
}

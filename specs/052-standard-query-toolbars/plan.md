# 实施
以 Carbon 表格工具栏与 MUI Data Grid 快速搜索的常见模式为参考，采用原生控件和共享 QueryToolbar.css，不引入组件依赖。四个页面统一为搜索输入、条件选择和操作按钮。移除招聘广场前一版专属分组样式，保留语义分组名称供辅助技术使用。

新增面经清除条件及分析自定义日期显隐单元测试；浏览器在 1440、1024、768、375、320px 验证四页高度、控件边界、提交、重置与日期，检查截图。运行 lint、测试、覆盖率及生产构建，重新打包内嵌资源。

参考：https://www.carbondesignsystem.com/building-blocks/core/components/data-table/guidelines 和 https://mui.com/x/react-data-grid/components/quick-filter 。

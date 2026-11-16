/**
 * mate 模块：觅搭校园找搭子平台业务。
 *
 * 包结构（扁平，按域分包，不再往下套娃）：
 * - auth    M1 用户与认证（后端甲）
 * - match   M2 搭子匹配（后端甲）
 * - risk    M6 风控与内容安全（后端甲）
 * - im      M3 即时通讯（后端乙）
 * - trade   M4 交易与会员（后端乙）
 * - content M5 内容社区（后端乙）
 * - goods   M7 二手交易（后端乙）
 *
 * 每个域内按芋道约定分层：controller(admin/app) / service / dal(mysql,dataobject) / enums。
 * 跨模块只调接口或发事件，不直接 import 对方包。
 */
package cn.iocoder.yudao.module.mate;

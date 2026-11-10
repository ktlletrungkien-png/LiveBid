/**
 * Hợp đồng chung giữa các mảng và nghiệp vụ đấu giá. <b>Phụ trách: Lê Trung Kiên.</b>
 *
 * <ul>
 *   <li>Hợp đồng: {@code AuctionService}, {@code AuthService}, {@code ClientChannel}, {@code UserPrincipal},
 *       {@code ServiceException}, {@code AuctionLifecycleListener}.</li>
 *   <li>Cài đặt: {@code BusinessExecutor} (executor một luồng), {@code AuctionServiceImpl},
 *       {@code RoomManager}, {@code AuctionScheduler}, {@code AuctionSettings}.</li>
 * </ul>
 *
 * <p>Muốn đổi chữ ký interface trong package này thì mở issue/PR và báo cả nhóm trước.
 */
package com.livebid.server.core;

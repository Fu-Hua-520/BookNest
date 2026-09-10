package com.fuhua.booknest.server.service.impl;

import com.fuhua.booknest.common.context.BaseContext;
import com.fuhua.booknest.common.exception.BaseException;
import com.fuhua.booknest.pojo.dto.BooklistAddItemDTO;
import com.fuhua.booknest.pojo.dto.BooklistCreateDTO;
import com.fuhua.booknest.pojo.dto.BooklistItemDTO;
import com.fuhua.booknest.pojo.dto.BooklistUpdateDTO;
import com.fuhua.booknest.pojo.entity.Book;
import com.fuhua.booknest.pojo.entity.Booklist;
import com.fuhua.booknest.pojo.entity.BooklistItem;
import com.fuhua.booknest.pojo.entity.User;
import com.fuhua.booknest.pojo.vo.BooklistDetailVO;
import com.fuhua.booknest.pojo.vo.BooklistItemVO;
import com.fuhua.booknest.pojo.vo.BooklistVO;
import com.fuhua.booknest.server.mapper.BookMapper;
import com.fuhua.booknest.server.mapper.BooklistItemMapper;
import com.fuhua.booknest.server.mapper.BooklistMapper;
import com.fuhua.booknest.server.mapper.UserMapper;
import com.fuhua.booknest.server.service.BooklistService;
import com.github.pagehelper.PageHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class BooklistServiceImpl implements BooklistService {

    @Autowired
    private BooklistMapper booklistMapper;
    @Autowired
    private BooklistItemMapper booklistItemMapper;
    @Autowired
    private BookMapper bookMapper;
    @Autowired
    private UserMapper userMapper;

    @Override
    public BooklistVO createBooklist(BooklistCreateDTO dto) {
        // 校验必填字段（@Valid 之外再兜底）
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new BaseException("标题不能为空");
        }

        String booklistId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        // 处理初始条目：构建 BooklistItem 列表，sortOrder 缺省按顺序 1,2,3...
        List<BooklistItem> items = new ArrayList<>();
        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            int order = 1;
            for (BooklistItemDTO itemDto : dto.getItems()) {
                if (itemDto.getBookId() == null || itemDto.getBookId().trim().isEmpty()) {
                    throw new BaseException("书籍ID不能为空");
                }
                Integer sortOrder = itemDto.getSortOrder() == null ? order : itemDto.getSortOrder();
                items.add(BooklistItem.builder()
                        .id(UUID.randomUUID().toString())
                        .booklistId(booklistId)
                        .bookId(itemDto.getBookId())
                        .sortOrder(sortOrder)
                        .note(itemDto.getNote())
                        .build());
                order++;
            }
        }

        // 组装书单：visibility 缺省 0，计数字段初始化，bookCount 由初始条目数决定
        Integer visibility = dto.getVisibility() == null ? 0 : dto.getVisibility();
        Booklist booklist = Booklist.builder()
                .id(booklistId)
                .userId(BaseContext.getCurrentId())
                .title(dto.getTitle())
                .summary(dto.getSummary())
                .coverImage(dto.getCoverImage())
                .visibility(visibility)
                .likeCount(0)
                .collectCount(0)
                .bookCount(items.size())
                .createTime(now)
                .updateTime(now)
                .build();
        booklistMapper.insert(booklist);

        // 批量插入初始条目
        if (!items.isEmpty()) {
            booklistItemMapper.batchInsert(items);
        }

        return toBooklistVO(booklist);
    }

    @Override
    public BooklistVO getBooklistById(String id) {
        Booklist booklist = booklistMapper.selectById(id);
        if (booklist == null) {
            throw new BaseException("书单不存在");
        }
        // 私密书单访问控制
        checkAccess(booklist);
        return toBooklistVO(booklist);
    }

    @Override
    public BooklistDetailVO getBooklistDetail(String id) {
        Booklist booklist = booklistMapper.selectById(id);
        if (booklist == null) {
            throw new BaseException("书单不存在");
        }
        // 私密书单访问控制
        checkAccess(booklist);

        // 查询条目并填充书籍信息
        List<BooklistItemVO> itemVOs = new ArrayList<>();
        List<BooklistItem> items = booklistItemMapper.listByBooklistId(id);
        if (items != null) {
            for (BooklistItem item : items) {
                Book book = null;
                if (item.getBookId() != null && !item.getBookId().isEmpty()) {
                    book = bookMapper.selectById(item.getBookId());
                }
                itemVOs.add(BooklistItemVO.builder()
                        .id(item.getId())
                        .bookId(item.getBookId())
                        .book(book)
                        .note(item.getNote())
                        .sortOrder(item.getSortOrder())
                        .build());
            }
        }

        BooklistVO base = toBooklistVO(booklist);
        return BooklistDetailVO.builder()
                .id(base.getId())
                .title(base.getTitle())
                .summary(base.getSummary())
                .coverImage(base.getCoverImage())
                .userId(base.getUserId())
                .userName(base.getUserName())
                .userAvatar(base.getUserAvatar())
                .bookCount(base.getBookCount())
                .likeCount(base.getLikeCount())
                .collectCount(base.getCollectCount())
                .visibility(base.getVisibility())
                .createTime(base.getCreateTime())
                .updateTime(base.getUpdateTime())
                .items(itemVOs)
                .build();
    }

    @Override
    public List<BooklistVO> listBooklists(String userId, Integer page, Integer pageSize) {
        if (page == null || page <= 0) {
            page = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }
        if (userId != null && userId.trim().isEmpty()) {
            userId = null;
        }

        PageHelper.startPage(page, pageSize);
        // visibility=0 仅返回公开书单
        List<Booklist> booklists = booklistMapper.list(0, userId);

        List<BooklistVO> result = new ArrayList<>();
        if (booklists != null) {
            for (Booklist booklist : booklists) {
                result.add(toBooklistVO(booklist));
            }
        }
        return result;
    }

    @Override
    public List<BooklistVO> listMyBooklists() {
        List<Booklist> booklists = booklistMapper.selectByUserId(BaseContext.getCurrentId());
        List<BooklistVO> result = new ArrayList<>();
        if (booklists != null) {
            for (Booklist booklist : booklists) {
                result.add(toBooklistVO(booklist));
            }
        }
        return result;
    }

    @Override
    public void updateBooklist(String id, BooklistUpdateDTO dto) {
        Booklist booklist = booklistMapper.selectById(id);
        if (booklist == null) {
            throw new BaseException("书单不存在");
        }
        // 仅作者本人可操作
        checkOwner(booklist);

        booklist.setTitle(dto.getTitle());
        booklist.setSummary(dto.getSummary());
        booklist.setCoverImage(dto.getCoverImage());
        booklist.setVisibility(dto.getVisibility());
        booklist.setUpdateTime(LocalDateTime.now());
        booklistMapper.update(booklist);
    }

    @Override
    public void deleteBooklist(String id) {
        Booklist booklist = booklistMapper.selectById(id);
        if (booklist == null) {
            throw new BaseException("书单不存在");
        }
        // 仅作者本人可操作
        checkOwner(booklist);

        // 级联删除条目后删除书单
        booklistItemMapper.deleteByBooklistId(id);
        booklistMapper.deleteById(id);
    }

    @Override
    public BooklistItemVO addItem(String booklistId, BooklistAddItemDTO dto) {
        Booklist booklist = booklistMapper.selectById(booklistId);
        if (booklist == null) {
            throw new BaseException("书单不存在");
        }
        // 仅作者本人可操作
        checkOwner(booklist);

        // 校验书籍存在
        if (dto.getBookId() == null || dto.getBookId().trim().isEmpty()) {
            throw new BaseException("书籍ID不能为空");
        }
        Book book = bookMapper.selectById(dto.getBookId());
        if (book == null) {
            throw new BaseException("书籍不存在");
        }

        // sortOrder 自动 = 当前条目数 + 1
        int sortOrder = 1;
        List<BooklistItem> items = booklistItemMapper.listByBooklistId(booklistId);
        if (items != null && !items.isEmpty()) {
            sortOrder = items.size() + 1;
        }

        BooklistItem item = BooklistItem.builder()
                .id(UUID.randomUUID().toString())
                .booklistId(booklistId)
                .bookId(dto.getBookId())
                .sortOrder(sortOrder)
                .note(dto.getNote())
                .build();
        booklistItemMapper.insert(item);
        booklistMapper.incrementBookCount(booklistId);

        return BooklistItemVO.builder()
                .id(item.getId())
                .bookId(item.getBookId())
                .book(book)
                .note(item.getNote())
                .sortOrder(item.getSortOrder())
                .build();
    }

    @Override
    public void removeItem(String booklistId, String itemId) {
        Booklist booklist = booklistMapper.selectById(booklistId);
        if (booklist == null) {
            throw new BaseException("书单不存在");
        }
        // 仅作者本人可操作
        checkOwner(booklist);

        BooklistItem item = booklistItemMapper.selectById(itemId);
        if (item == null) {
            throw new BaseException("条目不存在");
        }
        // 校验条目归属
        if (item.getBooklistId() == null || !item.getBooklistId().equals(booklistId)) {
            throw new BaseException("条目不属于该书单");
        }

        booklistItemMapper.deleteById(itemId);
        booklistMapper.decrementBookCount(booklistId);
    }

    /**
     * 私密书单访问控制：私密且非作者本人不可见
     * @param booklist 书单
     */
    private void checkAccess(Booklist booklist) {
        Integer visibility = booklist.getVisibility();
        if (visibility != null && visibility == 1) {
            String currentId = BaseContext.getCurrentId();
            if (currentId == null || !currentId.equals(booklist.getUserId())) {
                throw new BaseException("书单不存在");
            }
        }
    }

    /**
     * 校验当前用户是否为书单作者
     * @param booklist 书单
     */
    private void checkOwner(Booklist booklist) {
        String currentId = BaseContext.getCurrentId();
        if (currentId == null || !currentId.equals(booklist.getUserId())) {
            throw new BaseException("无权限操作");
        }
    }

    /**
     * 组装书单卡片 VO（填充作者用户名与头像）
     * @param booklist 书单
     * @return 书单卡片 VO
     */
    private BooklistVO toBooklistVO(Booklist booklist) {
        String userName = null;
        String userAvatar = null;
        if (booklist.getUserId() != null) {
            User user = userMapper.getUserById(booklist.getUserId());
            if (user != null) {
                userName = user.getUsername();
                userAvatar = user.getAvatar();
            }
        }

        return BooklistVO.builder()
                .id(booklist.getId())
                .title(booklist.getTitle())
                .summary(booklist.getSummary())
                .coverImage(booklist.getCoverImage())
                .userId(booklist.getUserId())
                .userName(userName)
                .userAvatar(userAvatar)
                .bookCount(booklist.getBookCount())
                .likeCount(booklist.getLikeCount())
                .collectCount(booklist.getCollectCount())
                .visibility(booklist.getVisibility())
                .createTime(booklist.getCreateTime())
                .updateTime(booklist.getUpdateTime())
                .build();
    }
}

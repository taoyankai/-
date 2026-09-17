package com.hz.delivery.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hz.delivery.entity.Notice;
import com.hz.delivery.mapper.NoticeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NoticeService {

    private final NoticeMapper noticeMapper;

    /** C 端：只返回展示中的 */
    public List<Notice> listPublic() {
        return noticeMapper.selectList(new LambdaQueryWrapper<Notice>()
                .eq(Notice::getStatus, 1)
                .orderByAsc(Notice::getSort)
                .orderByDesc(Notice::getCreateTime));
    }

    public List<Notice> listAdmin() {
        return noticeMapper.selectList(new LambdaQueryWrapper<Notice>()
                .orderByAsc(Notice::getSort)
                .orderByDesc(Notice::getCreateTime));
    }

    public Notice save(Notice n) {
        if (n.getId() == null) {
            if (n.getStatus() == null) n.setStatus(1);
            if (n.getSort() == null) n.setSort(0);
            noticeMapper.insert(n);
        } else {
            noticeMapper.updateById(n);
        }
        return noticeMapper.selectById(n.getId());
    }

    public void delete(Long id) {
        noticeMapper.deleteById(id);
    }
}

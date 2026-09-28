package com.tea.factory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.tea.factory.domain.WitheringTrough;

public interface WitheringTroughRepository extends JpaRepository<WitheringTrough, Long> {

    WitheringTrough findByTroughNo(String troughNo);

    List<WitheringTrough> findByStatusOrderByTroughNoAsc(String status);

    /**
     * 手写 JPQL：摊青达标率统计（按茶类汇总槽数、已达标槽数、平均失水率）。
     */
    @Query("select t.teaType, count(t), sum(case when t.currentLossPct >= t.targetLossPct then 1 else 0 end), "
            + "avg(t.currentLossPct) "
            + "from WitheringTrough t "
            + "group by t.teaType")
    List<Object[]> summarizeReadyRate();
}

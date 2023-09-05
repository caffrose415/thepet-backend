package com.thehyundai.thepet.domain.heendycar;

import java.util.List;

public interface HcService {

    HcBranchVO showBranchInfo(String branchCode);

    List<HcBranchVO> showAllBranches();

    HcReservationVO createReservation(String token, HcReservationVO requestVO);

}
package com.thehyundai.thepet.domain.mypet.pet;


import com.thehyundai.thepet.global.cmcode.CmCodeVO;

import java.util.List;

public interface PetService {

    Integer registerClub(String token,PetVO petVO);

    Integer updateFeed(PetVO petVO);

    List<PetVO> myPet(String token);

    List<CmCodeVO> getAllCode();

    List<PetVO> findPetsWithAllergies(Integer memberId);
}
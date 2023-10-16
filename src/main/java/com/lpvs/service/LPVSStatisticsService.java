/**
 * Copyright 2023 Basaeng, kyudori, hwan5180, quswjdgma83
 *
 * Use of this source code is governed by a MIT license that can be
 * found in the LICENSE file.
 */

package com.lpvs.service;

import com.lpvs.entity.LPVSDetectedLicense;
import com.lpvs.entity.LPVSMember;
import com.lpvs.entity.LPVSPullRequest;
import com.lpvs.entity.dashboard.Dashboard;
import com.lpvs.entity.dashboard.DashboardElementsByDate;
import com.lpvs.entity.enums.Grade;
import com.lpvs.exception.WrongAccessException;
import com.lpvs.repository.LPVSDetectedLicenseRepository;
import com.lpvs.repository.LPVSLicenseRepository;
import com.lpvs.repository.LPVSMemberRepository;
import com.lpvs.repository.LPVSPullRequestRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.*;

@Service
@Slf4j
public class LPVSStatisticsService {
    private LPVSPullRequestRepository lpvsPullRequestRepository;
    private LPVSDetectedLicenseRepository lpvsDetectedLicenseRepository;
    private LPVSLoginCheckService loginCheckService;
    private LPVSLicenseRepository lpvsLicenseRepository;
    private LPVSMemberRepository memberRepository;


    public LPVSStatisticsService(LPVSPullRequestRepository lpvsPullRequestRepository, LPVSDetectedLicenseRepository lpvsDetectedLicenseRepository,
                                 LPVSLoginCheckService loginCheckService, LPVSLicenseRepository lpvsLicenseRepository,
                                 LPVSMemberRepository memberRepository) {
        this.lpvsPullRequestRepository = lpvsPullRequestRepository;
        this.lpvsDetectedLicenseRepository = lpvsDetectedLicenseRepository;
        this.loginCheckService = loginCheckService;
        this.lpvsLicenseRepository = lpvsLicenseRepository;
        this.memberRepository = memberRepository;
    }

    public List<LPVSPullRequest> pathCheck(String type, String name, Authentication authentication) {
        LPVSMember findMember = loginCheckService.getMemberFromMemberMap(authentication);

        String findNickName = findMember.getNickname();
        String findOrganization = findMember.getOrganization();

        List<LPVSPullRequest> prList = new ArrayList<>();

        if (type.equals("own") && findNickName.equals(name) ||
                type.equals("org") && findOrganization.equals(findOrganization)) {
            prList = lpvsPullRequestRepository.findByPullRequestBase(name);
        } else if (type.equals("send") && findNickName.equals(name)) {
            prList = lpvsPullRequestRepository.findBySenderOrPullRequestHead(name);
        } else {
            throw new WrongAccessException("WrongPathException");
        }

        return prList;
    }
    public Dashboard getDashboardEntity(String type, String name, Authentication authentication) {

        int totalDetectionCount = 0;
        int highSimilarityCount = 0;
        int totalIssueCount = 0;
        int totalParticipantsCount = 0;
        int totalRepositoryCount = 0;
        Set<String> participantsSet = new HashSet<>();


        List<LPVSPullRequest> prList = pathCheck(type, name, authentication);
        Map<String, Integer> licenseCountMap = new HashMap<>();
        List<DashboardElementsByDate> dashboardByDates = new ArrayList<>();
        Map<LocalDate, List<LPVSPullRequest>> datePrMap = new HashMap<>();

        List<String> allSpdxId = lpvsLicenseRepository.takeAllSpdxId();
        for (String spdxId : allSpdxId) {
            licenseCountMap.put(spdxId, 0);
        }

        Set<String> totalSenderSet = new HashSet<>();
        Set<String> totalRepositorySet = new HashSet<>();
        for (LPVSPullRequest pr : prList) {
            LocalDate localDate = new Date(pr.getDate().getTime()).toLocalDate();
            List<LPVSPullRequest> datePrMapValue = datePrMap.get(localDate);
            if (datePrMapValue == null) {
                datePrMapValue = new ArrayList<>();
                datePrMap.put(localDate, datePrMapValue);
            }
            datePrMapValue.add(pr);

            totalSenderSet.add(pr.getSender());
            if (!(pr.getRepositoryName()==null || pr.getRepositoryName().isEmpty())) {
                totalRepositorySet.add(pr.getRepositoryName());
            }
        }
        totalSenderSet.remove(null);

        for (LocalDate localDate : datePrMap.keySet()) {
            Map<Grade, Integer> riskGradeMap = new HashMap<>();
            riskGradeMap = putDefaultriskGradeMap(riskGradeMap);

            Set<String> senderSet = new HashSet<>();
            List<LPVSPullRequest> prByDate = datePrMap.get(localDate);
            for (LPVSPullRequest pr : prByDate) {
                List<LPVSDetectedLicense> dlList = lpvsDetectedLicenseRepository.findNotNullDLByPR(pr);
                if (!(pr.getRepositoryName()==null || pr.getRepositoryName().isEmpty())) {
                    senderSet.add(pr.getSender());
                }
                for (LPVSDetectedLicense dl : dlList) {
                    Grade grade = null;
                    if (dl.getMatch() != null) {
                        grade = getGrade(dl.getMatch());
                        riskGradeMap.put(grade, riskGradeMap.getOrDefault(grade, 0) + 1);
                    }
                    if (dl.getLicense() != null) {
                        licenseCountMap.put(dl.getLicense().getSpdxId(), licenseCountMap.get(
                                dl.getLicense().getSpdxId()) + 1);
                    }

                    if (grade == Grade.HIGH) {
                        highSimilarityCount += 1;
                    }
                    if (dl.getIssue()) {
                        totalIssueCount += 1;
                    }
                }
                totalDetectionCount += dlList.size();
            }

            senderSet.remove(null);
            dashboardByDates.add(new DashboardElementsByDate(localDate, senderSet.size(),
                    prByDate.size(), riskGradeMap));

        }

        for (String s : totalSenderSet) {
            log.info(s);
        }

        totalParticipantsCount = totalSenderSet.size();
        totalRepositoryCount = totalRepositorySet.size();
        return new Dashboard(name, licenseCountMap, totalDetectionCount, highSimilarityCount, totalIssueCount,
                totalParticipantsCount, totalRepositoryCount, dashboardByDates);
    }

    public Grade getGrade(String match) {
        int matchValue = Integer.parseInt(match.substring(0, match.length() - 1));

        if (matchValue >= 80) {
            return Grade.HIGH;
        } else if (matchValue >= 50) {
            return Grade.MIDDLE;
        } else if (matchValue >= 30) {
            return Grade.LOW;
        } else {
            return Grade.NONE;
        }
    }

    public Map<Grade, Integer> putDefaultriskGradeMap(Map<Grade, Integer> riskGradeMap) {
        riskGradeMap.put(Grade.HIGH, 0);
        riskGradeMap.put(Grade.MIDDLE, 0);
        riskGradeMap.put(Grade.LOW, 0);
        riskGradeMap.put(Grade.NONE, 0);

        return riskGradeMap;
    }
}
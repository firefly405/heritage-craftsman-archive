package com.qizixuan.heritage.service;
import com.qizixuan.heritage.common.PhoneNumbers;
import com.qizixuan.heritage.entity.ArtisanProfile;
import java.time.Year;
import java.util.*;
public final class ArchiveQuality {
 private ArchiveQuality(){}
 public static Map<String,Object> assess(ArtisanProfile p){
  List<String> missing=new ArrayList<>();
  if(blank(p.getName()))missing.add("姓名");
  if(blank(p.getRegion()))missing.add("所在地区");
  if(p.getCategoryId()==null)missing.add("技艺类目");
  if(blank(p.getPhone()))missing.add("联系电话");
  if(p.getBirthYear()==null)missing.add("出生年份");
  if(p.getExperienceYears()==null)missing.add("从业年限");
  if(blank(p.getSkillDescription()))missing.add("技艺介绍");
  if(blank(p.getInheritanceStory()))missing.add("传承经历");
  List<String> warnings=new ArrayList<>();
  int year=Year.now().getValue();
  if(!blank(p.getPhone())&&!PhoneNumbers.isValid(p.getPhone()))warnings.add("手机号格式不正确");
  if(p.getBirthYear()!=null&&(p.getBirthYear()<1900||p.getBirthYear()>year))warnings.add("出生年份不在有效范围");
  if(p.getExperienceYears()!=null&&(p.getExperienceYears()<0||(p.getBirthYear()!=null&&p.getExperienceYears()>year-p.getBirthYear())))warnings.add("从业年限与年龄不一致");
  return Map.of("id",p.getId()==null?0L:p.getId(),"name",p.getName()==null?"未填写":p.getName(),"score",(8-missing.size())*100/8,"missing",missing,"warnings",warnings,"ready",missing.isEmpty()&&warnings.isEmpty());
 }
 private static boolean blank(String s){return s==null||s.isBlank();}
}

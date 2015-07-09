/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.wirecard.ezlinkwebservices.services.impl;

import com.ezlinkwebservices.service.reciept.RecieptFault;
import com.ezlinkwebservices.service.reciept.request.EZLINGWSREQENV;
import com.ezlinkwebservices.service.reciept.RecieptFault_Exception;
import com.ezlinkwebservices.service.reciept.response.RecieptRes;
import com.wirecard.ezlinkwebservices.dto.EMerchantDetailsDto;
import com.wirecard.ezlinkwebservices.mapperdao.EMerchantDetailsDtoMapper;
import com.wirecard.ezlinkwebservices.constants.StringConstants;
import com.wirecard.ezlinkwebservices.dto.EBatchLogDto;
import com.wirecard.ezlinkwebservices.dto.ETerminalDataDto;
import com.wirecard.ezlinkwebservices.dto.ETranxLogDto;
import com.wirecard.ezlinkwebservices.mapperdao.EBatchLogDtoMapper;
import com.wirecard.ezlinkwebservices.mapperdao.ETerminalDataDtoMapper;
import com.wirecard.ezlinkwebservices.mapperdao.ETranxLogDtoMapper;

import com.wirecard.ezlinkwebservices.services.RecieptService;
import com.wirecard.ezlinkwebservices.util.TerminalUtil;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.support.SpringBeanAutowiringSupport;
import org.transinfo.messaging.communication.posdailer.SerialManager;

/**
 *
 * @author WCCTTI-JANAHAN
 */
@Service
public class RecieptServiceImpl implements RecieptService {

    @Autowired
    EMerchantDetailsDtoMapper objEMerchantDetailsDtoMapper;
    @Autowired
    ETranxLogDtoMapper objETranxLogDtoMapper;
    @Autowired
    ETerminalDataDtoMapper objETerminalDataDtoMapper;
    @Autowired
    ETerminalDataDto objETerminalDataDto;
    @Autowired 
    EBatchLogDto objBatchLogDto;
    @Autowired
    EBatchLogDtoMapper objBatchLogDtoMapper;
    int update;
    
    private static final org.apache.log4j.Logger ezlink = org.apache.log4j.Logger.getLogger(RecieptServiceImpl.class);

    @Override
    public RecieptRes getReciept(EZLINGWSREQENV parameters) throws RecieptFault_Exception {

        ezlink.info("Reciept Request received in " + RecieptServiceImpl.class.getName());

        RecieptRes objRecieptRes = new RecieptRes();
        Date updatedDate = new Date();
        List<ETranxLogDto> tranxLogDtoList;
        List<ETerminalDataDto> terminalDataDtoList;
        int no_success = 0;
        int no_failure = 0;
        
        
        try {
            SpringBeanAutowiringSupport.processInjectionBasedOnCurrentContext(this);
            tranxLogDtoList = objETranxLogDtoMapper.getTranxSuccess();

            if (null!=tranxLogDtoList && !tranxLogDtoList.isEmpty()) {
                terminalDataDtoList = objETerminalDataDtoMapper.noReceiptDecryption(tranxLogDtoList);
                if (null != terminalDataDtoList && !terminalDataDtoList.isEmpty()) {
                    String decryptedRecieptData = null;
                    
                    for (int i = 0; i < terminalDataDtoList.size(); i++) {
                        objETerminalDataDto = terminalDataDtoList.get(i);
                        objETerminalDataDto.setUpdatedDate(updatedDate);

                        try {
                            SerialManager objSerialManager = new SerialManager();
                            Thread.sleep(5000);
                            
                            synchronized (this) {
                                decryptedRecieptData = objSerialManager.getDecryptedRecieptData(objETerminalDataDto);
                            }
                            objETerminalDataDto.setDecryptedRecieptData(decryptedRecieptData);
                            objETerminalDataDto.setDecryptReceiptStatus("D");

                            // get Decrypt success, update Terminal_Data
                            update = objETerminalDataDtoMapper.updateETerminalDataBySNo(objETerminalDataDto);
                            if (update == 1) {
                                no_success++;
                            } else {
                                ezlink.info("-------------------Update TERMINAL_DATA unsuccessful------------------");
                                ezlink.info("-------------------Terminal ID: " + objETerminalDataDto.getSno() +"------------------");
                            }

                        } catch (Exception e) {
                            objETerminalDataDto.setDecryptReceiptStatus("E");
                            update = objETerminalDataDtoMapper.updateETerminalDataBySNo(objETerminalDataDto);
                            if(update == 1) {
                                ezlink.info("-------------------Update TERMINAL_DATA unsuccessful------------------");
                                ezlink.info("-------------------Terminal ID: " + objETerminalDataDto.getSno() +"------------------");
                            }
                            e.printStackTrace();
                        }
                        // Close port if needed
                        no_failure = terminalDataDtoList.size() - no_success;
                    }
//                    objSerialManager.shutdown();
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        try {
            // Insert Batch_log
            objBatchLogDto.setNoSuccess(no_success);
            objBatchLogDto.setNoFailure(no_failure);
            objBatchLogDto.setUpdateDate(updatedDate);
        
            objBatchLogDtoMapper.insert(objBatchLogDto);
            ezlink.info("-------------------Insert new Batch_Log ------------------");
        } catch (SQLException ex) {
            Logger.getLogger(RecieptServiceImpl.class.getName()).log(Level.SEVERE, null, ex);
        }
        return objRecieptRes;
    }
}

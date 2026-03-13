package com.eliasgonzalez.cartones.excel.interfaces;

import com.eliasgonzalez.cartones.vendedor.dto.FilasIgnoradasDTO;
import org.springframework.web.multipart.MultipartFile;

public interface IExcelService {

    FilasIgnoradasDTO leerExcel(MultipartFile file, String procesoIdCreado);
}

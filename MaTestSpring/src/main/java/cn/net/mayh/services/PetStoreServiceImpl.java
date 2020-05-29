package cn.net.mayh.services;


import cn.net.mayh.jpa.JpaAccountDao;
import cn.net.mayh.jpa.JpaItemDao;
import org.springframework.stereotype.Service;

@Service
public class PetStoreServiceImpl {
    private JpaAccountDao accountDao;
    private JpaItemDao itemDao;

    public void setAccountDao(JpaAccountDao accountDao) {
        this.accountDao = accountDao;
    }

    public void setItemDao(JpaItemDao itemDao) {
        this.itemDao = itemDao;
    }

    public PetStoreServiceImpl() {
    }
}

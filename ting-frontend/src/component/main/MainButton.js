import React from 'react';
import { Link } from 'react-router-dom';
import styles from './MainButton.module.css';

const MainButton = () => {
  return (
    <div className={styles.container}>
         <Link to="/matching" className={styles.linkButton}>
        <button className={styles.btn}>팅하러가기</button>
      </Link>

    </div>
  );
};

export default MainButton;
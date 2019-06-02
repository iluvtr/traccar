package org.traccar.protocol;

import org.junit.Test;
import org.traccar.ProtocolTest;
import org.traccar.model.Position;

public class ItsProtocolDecoderTest extends ProtocolTest {

    @Test
    public void testDecode() throws Exception {

        ItsProtocolDecoder decoder = new ItsProtocolDecoder(null);

        verifyPosition(decoder, text(
                "$RLP,N.A,2.0.2,NR,01,L,869867030181814,N.A,28022019,180155,1,28.688226,N,076.993570,E,0.0,80.26,17,201.0,0.89,0.60,VODAFONE I,0,1,25.0,4.20,0,C,14,404,11,415,F34A,51f7,415,13,840b,415,8,a3f7,0c2,5,ef77,415,5,0001,00,17888,47,*"));

        verifyAttribute(decoder, text(
                "$TEL123,Teltonika,03.18.16,NR,1,L,352093085223096,KA09X6945,1,24122018,055749,12.303873,N,76.690697,E,0.0,349.00,10,795.0,0.50,0.40,Airtel,0,1,14.6,4.1,0,,28,404,45,625A,116E,29,28DF,03A3,28,9A5C,0923,26,116F,625A,25,2A51,03A3,0010,00,000042,0.1,0.1,0,()*7B"),
                Position.PREFIX_ADC + 2, 0.1);

        verifyPosition(decoder, text(
                "$Header,iTriangle,1_37T02B0164MAIS,BR,6,L,861693034634154,KA01I2000,1,09112017,160702,12.976593,N,77.549782,E,25.1,344,15,911.0,1.04,0.68,Airtel,1,1,11.8,3.8,1,C,24,404,45,61b4,9ad9,31,9adb,61b4,35,ffff,0000,33,ffff,0000,31,ffff,0000,0001,00,000014,0.0,0.1,4,()*1E"));

        verifyAttribute(decoder, text(
                "$EPB,EMR,869867036066035,NM,03042019,192008,V,000.00000000,N,000.00000000,E,0000000000.0,0000.0,00.000,G,,0,404,22,ECFB,36EF*226F7BD1"),
                Position.KEY_ALARM, Position.ALARM_SOS);

        verifyPosition(decoder, text(
                "$,CP,ATL,1.4D3_AIS140_1.0,EA,10,H,868728037717441,,1,31032019,140054,28.533699,N,77.269020,E,0.0,188.00,14,76.0,1.3,0.0,Idea,0,1,12.7,3.9,1,O,22,404,11,69,979c,fc1,69,18,fbf,69,15,e36e,69,14,ba2f,3ff,13,0111,00,249404,"));

        verifyPosition(decoder, text(
                "$NRM,ROADRPA,1.8AIS,EA,01,L,869867036341099,,1,11032019,231048,19.25166667,N,73.04615167,E,0.0,230.21,17,12.8,0.80,0.80,airtel,0,1,13.5,4.2,0,O,22,404,90,0CC9,EBC8,19,0CC9,8992,16,0CC9,AB49,15,0CC9,AB44,14,0CC9,F03C,0000,00,002080,C9FBBF99"));

        verifyPosition(decoder, text(
                "$NRM,ROADRPA,1.7AIS,NR,01,L,869867036345389,,1,25022019,051716,25.12891000,N,75.85587833,E,7.6,350.00,14,284.8,1.00,1.00,AIRTEL,1,1,28.0,4.2,0,C,13,404,70,4E3B,3C84,11,4E3B,39B8,08,4E3B,3965,07,4E3B,48B5,07,4E3B,3C85,0000,00,000551,71978C6B"));

        verifyPosition(decoder, text(
                "$,03,XYZ123,0.0.1,TA,16,L,869867035297185,MH12AB1234,1,20,02,2019,10,59,13,023.482630,N,086.399673,E,000.1,015.19,21,212.3,01.12,00.58,NOSERV,0,1,00.0,4.6,1,C,11,404,75,082a,db3a,0000,0000,0000,0000,0000,0000,0000,0000,0000,0000,0000,0000,0000,01,000013,01dbd51f,"));

        verifyPosition(decoder, text(
                "$NRM,ROADRPA,1.7AIS,NR,01,L,869867036345389,,1,25022019,051716,25.12891000,N,75.85587833,E,7.6,350.00,14,284.8,1.00,1.00,AIRTEL,1,1,28.0,4.2,0,C,13,404,70,4E3B,3C84,11,4E3B,39B8,08,4E3B,3965,07,4E3B,48B5,07,4E3B,3C85,0000,00,000551,71978C6B"));

        verifyPosition(decoder, text(
                "$,1,CHVTS,CHVTS1.0,DT,16,L,861359039868243,861359039868243,1,05022019,071225,19.965062,N,73.736088,E,0,050,03,0632,6.67,6.75,Idea Cel,1,1,23.96,4.0,0,W,28,404,004,4e2b,49e,4e2bea86727ab3d6704e2bea7714e2be9d72,0000,00,001133,232"));

        verifyPosition(decoder, text(
                "$,04,XYZ123,0.0.1,TA,16,L,861359034100626,MH12AB1234,1,12,11,2018,08,53,08,018.489645,N,073.855972,E,000.0,220.04,12,593.0,01.13,00.75,AIRTEL,1,1,00.0,4.1,1,C,18,404,90,0c23,781a,5169,0c23,-093,0000,0000,0000,0000,0000,0000,0000,0000,0000,1000,01,000006,f906c65c,"));

        verifyPosition(decoder, text(
                "$,A,MFR,7.0,NR,01,L,869026034780985,PJ09BU1234,1,12112018,121953,12.756974,N,077.800025,E,000.0,318.03,15,794.0,001.3,000.7,TAMIL NAD,0,1,13.08,04.13,0,O,22,404,80,0919,71C1,0919,7168,19,0919,71c3,17,0919,71c2,11,0919,7167,09,0011,00,000173,D8,000000.00,,,"));

        verifyPosition(decoder, text(
                "$,04,XYZ123,0.0.1,TA,16,L,861359034100626,MH12AB1234,1,14,10,2018,04,50,52,018.489624,N,073.855980,E,000.0,039.86,13,584.1,01.11,00.75,AIRTEL,1,1,00.0,4.1,1,C,15,404,90,0c23,781a,0000,0000,0000,0000,0000,0000,0000,0000,0000,0000,0000,0000,1000,01,000005,13b75499,"));

        verifyNull(decoder, text(
                "$,01,XYZ123,0.0.1,861359034137271,MH12AB1234,"));

        verifyNull(decoder, text(
                "$,02,XYZ123,0.0.1,861359034137271,100,30,00.0,00005,00600,1000,01,00.1,00.0,"));

        verifyPosition(decoder, text(
                "$,EPB,EMR,861359034100626,SP,00,00,0000,00,00,00,V,000.000000,N,000.000000,E,000.0,000.0,000.00,N,MH12AB1234,0000000000000,d34679e1,"));

        verifyPosition(decoder, text(
                "$,03,XYZ123,0.0.1,TA,16,L,861359034137271,MH12AB1234,0,00,00,0000,00,00,00,000.000000,N,000.000000,E,000.0,000.00,00,000.0,00.00,00.00,IDEAIN,1,1,00.0,4.0,1,O,16,404,22,2797,11b7,11b9,2797,-087,11b8,2797,-093,11b4,2797,-106,0000,0000,0000,1000,01,000032,8173e058,"));

        verifyPosition(decoder, text(
                "$,04,XYZ123,0.0.1,BR,06,L,861359034137271,MH12AB1234,0,00,00,0000,00,00,00,000.000000,N,000.000000,E,000.0,000.00,00,000.0,00.00,00.00,IDEAIN,1,1,00.0,3.8,1,O,17,404,22,2797,11b7,11b9,2797,-093,11b8,2797,-098,0000,0000,0000,0000,0000,0000,1000,00,000006,abd26284,"));

    }

}
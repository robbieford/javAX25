fontSize = 19;
% .wav file	Filtering	Correlation	Zero Xing	Strict Xing	Windowed Zero	Peak	Derivative	Goertzel	PLL	PreClocking	Goertzel Pre
% OT3Test	None        40          35          40          39              3       40          40          40	0           40	
%           Bandpass	40          19          0           0               0       33          40          40	40          28	
%           6db         40          12          40          0               0       14          40          0	39          15	
% Gen200	None        200         200         200         200             200     200         200         200	0           200	
%           Bandpass	200         27          200         0               0       200         200         200	200         106	
%           6db         200         200         200         0               0       200         200         200	200         199	
% OT3WNoise	None        24          0           6           0               0       3           25          21	0           10	
%           Bandpass	24          6           2           0               0       5           23          23	17          11	
%           6db         11          1           9           0               0       4           6           14	6           5	
% LATrack1	None        946         460         568         298             0       543         965         925	0           564	
%           Bandpass	964         536         151         0               0       422         965         654	939         431	
%           6db         914         53          422         0               0       97          512         189	482         224	
% LATrack2	None        639         0           0           162             0       5           608         0	0           390	
%           Bandpass	872         0           0           0               0       0           448         0	939         230	
%           6db         958         209         74          0               0       254         956         0	947         392	

x = [11          1           9           0               0       4           6           14	6           5];
y = ['Correlation' 'Zero Crossing' 'Strict Zero' 'Windowed Zero' 'Peak' 'Derivative' 'Goertzel' 'PLL' 'Mixed Preclocking' 'Goertzel Preclocking'];
f = figure('Position',[0,0,1280,1024]);
set(gcf,'color','w');
bar(x);
filename = 'Software Performance on Noisy OT3 w/ Emphasis Filter';
title(filename);
%xlabel('Devices');
ylabel('Number of Packets Decoded');
set(gca,'XTickLabel',{'Correlation' 'Zero Crossing' 'Strict Zero' 'Windowed Zero' 'Peak' 'Derivative' 'Goertzel' 'PLL' 'Mixed Preclocking' 'Goertzel Preclocking'})
set(gca,'FontSize',fontSize, 'FontName', 'Times New Roman');
set(findall(gcf,'type','text'),'FontSize',fontSize, 'FontName', 'Times New Roman');
rotateXLabels(gca, 45)
set(gca, 'xtick', [])
grid on
saveas(f, strcat('.\..\..\..\rrxthesis\images\',regexprep(filename,'[^\w'']',''),'.png'));
pause();
close(f);
clear all;
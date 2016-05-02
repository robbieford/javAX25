fontSize = 20;
%           OT2	OT3	OT USB	OT3 Micro	Kam	My Kam Plus	Kam Plus	MFJ-1278	My PK-88	PK-88	PK-232	PK-232MBX
% OT3       40	40	39      40          40	40          40          40          40          40      40      40
% Gen200    200	200	193     199         200	200         200         200         200                 200     200
% OT3 Noise 5	16	13      12          20	12          12          5           25          25      12      18
% Track 1   947	937	869     830         988	958         985         837         981         1007	918     964
% Track 2   927	445	633     263         938	967         998         937         866         633     929     799


x = [5	16	13      12          20	12          12          5           25          25      12      18];
y = ['OT2' 'OT3' 'OT USB' 'OT3 Micro' 'Kam' 'Kam Plus (1)' 'Kam Plus (2)' 'MFJ-1279' 'PK-88 (1)' 'PK-88 (2)' 'PK-232' 'PK-232MBX'];
f = figure('Position',[0,0,1280,1024]);
set(gcf,'color','w');
bar(x);
ylim([0,28]);
snr = 20./x;
for i1=1:numel(x)
    text(i1,x(i1),num2str(snr(i1),'%0.1f'),...
               'HorizontalAlignment','center',...
               'VerticalAlignment','bottom')
end
filename = 'Performance of All Hardware on OT3 Test with Noise';
title(filename);
%xlabel('Devices');
ylabel('Number of Packets Decoded');
set(gca,'XTickLabel',{'OT2' 'OT3' 'OT USB' 'OT3 Micro' 'Kam' 'Kam Plus (1)' 'Kam Plus (2)' 'MFJ-1279' 'PK-88 (1)' 'PK-88 (2)' 'PK-232' 'PK-232MBX'})
set(gca,'FontSize',fontSize, 'FontName', 'Times New Roman');
set(findall(gcf,'type','text'),'FontSize',fontSize, 'FontName', 'Times New Roman');
rotateXLabels(gca, 45)
set(gca, 'xtick', [])
grid on
saveas(f, strcat('.\..\..\..\rrxthesis\images\',regexprep(filename,'[^\w'']',''),'.png'));
pause();
close(f);
clear all;